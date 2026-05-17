package io.github.tissyboxc.clark_aams_backend.school;

import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import io.github.tissyboxc.clark_aams_backend.importers.schools.SchoolConfigProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SchoolService {
    private static final String DEFAULT_LESSON_TIME_PROFILE = "general-a";
    private static final Map<String, List<LessonTimeDto>> LESSON_TIME_PROFILES = Map.of(
            "general-a", lessons(
                    "08:30",
                    "09:20",
                    "10:25",
                    "11:15",
                    "13:30",
                    "14:20",
                    "15:25",
                    "16:15"
            ),
            "general-b", lessons(
                    "08:00",
                    "08:55",
                    "10:00",
                    "10:55",
                    "13:30",
                    "14:25",
                    "14:30",
                    "16:25"
            )
    );

    private final Map<String, SchoolEntity> schools;

    public SchoolService(List<SchoolConfigProvider> schoolConfigProviders) {
        this.schools = schoolConfigProviders.stream()
                .map(SchoolConfigProvider::school)
                .collect(Collectors.toUnmodifiableMap(
                        school -> school.id().toLowerCase(Locale.ROOT),
                        Function.identity()
                ));
    }

    public List<SchoolDto> listEnabledSchools() {
        return schools.values().stream()
                .filter(SchoolEntity::enabled)
                .map(this::toDto)
                .toList();
    }

    public SchoolDto getSchool(String schoolId) {
        SchoolEntity school = schools.get(normalize(schoolId));
        if (school == null) {
            throw new BusinessException(ErrorCode.SCHOOL_NOT_FOUND);
        }
        return toDto(school);
    }

    public SchoolLessonTimesDto getLessonTimes(String schoolId) {
        SchoolEntity school = schools.get(normalize(schoolId));
        if (school == null) {
            throw new BusinessException(ErrorCode.SCHOOL_NOT_FOUND);
        }
        String profile = resolveProfile(school.lessonTimeProfile());
        List<LessonTimeDto> lessons = LESSON_TIME_PROFILES.get(profile);
        return new SchoolLessonTimesDto(school.id(), profile, lessons);
    }

    public List<SchoolLessonTimesDto> listLessonTimeProfiles() {
        return LESSON_TIME_PROFILES.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new SchoolLessonTimesDto("", entry.getKey(), entry.getValue()))
                .toList();
    }

    private SchoolDto toDto(SchoolEntity entity) {
        return new SchoolDto(
                entity.id(),
                entity.name(),
                entity.shortName(),
                entity.enabled(),
                new SchoolCapabilityDto(entity.academicImportEnabled(), entity.imageRecognitionEnabled()),
                new SchoolLoginDto(entity.loginMode(), entity.loginUrl(), List.of(entity.successUrlPatterns())),
                resolveProfile(entity.lessonTimeProfile()),
                entity.version()
        );
    }

    private String normalize(String schoolId) {
        return schoolId == null ? "" : schoolId.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeProfile(String profile) {
        String normalized = normalize(profile);
        return normalized.isBlank() ? DEFAULT_LESSON_TIME_PROFILE : normalized;
    }

    private String resolveProfile(String profile) {
        String normalized = normalizeProfile(profile);
        return LESSON_TIME_PROFILES.containsKey(normalized) ? normalized : DEFAULT_LESSON_TIME_PROFILE;
    }

    private static List<LessonTimeDto> lessons(String... startTimes) {
        return java.util.stream.IntStream.rangeClosed(1, 13)
                .mapToObj(lesson -> new LessonTimeDto(lesson, startTime(startTimes, lesson)))
                .toList();
    }

    private static String startTime(String[] startTimes, int lesson) {
        int index = lesson - 1;
        if (index < 0 || index >= startTimes.length) {
            return "";
        }
        return startTimes[index];
    }
}
