package io.github.tissyboxc.clark_aams_backend.importers.dto;

import java.util.List;

public record ImportCourseResponse(
        String schoolId,
        Integer importerVersion,
        List<CourseDto> courses,
        List<String> warnings
) {
}
