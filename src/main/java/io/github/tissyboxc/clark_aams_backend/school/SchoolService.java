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

    private SchoolDto toDto(SchoolEntity entity) {
        return new SchoolDto(
                entity.id(),
                entity.name(),
                entity.shortName(),
                entity.enabled(),
                new SchoolCapabilityDto(entity.academicImportEnabled(), entity.imageRecognitionEnabled()),
                new SchoolLoginDto(entity.loginMode(), entity.loginUrl(), List.of(entity.successUrlPatterns())),
                entity.version()
        );
    }

    private String normalize(String schoolId) {
        return schoolId == null ? "" : schoolId.trim().toLowerCase(Locale.ROOT);
    }
}
