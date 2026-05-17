package io.github.tissyboxc.clark_aams_backend.school;

import java.util.List;

public record SchoolLessonTimesDto(
        String schoolId,
        String profile,
        List<LessonTimeDto> lessons
) {
}
