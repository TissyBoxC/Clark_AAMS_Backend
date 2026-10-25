package io.github.tissyboxc.clark_aams_backend.importers.dto;

import java.util.List;

public record HtmlImportCourseResponse(
        String status,
        String schoolId,
        String schoolName,
        Integer importerVersion,
        List<CourseDto> courses,
        List<String> warnings,
        String savedFileName
) {
    public static HtmlImportCourseResponse parsed(
            String schoolId,
            String schoolName,
            Integer importerVersion,
            List<CourseDto> courses,
            List<String> warnings
    ) {
        return new HtmlImportCourseResponse(
                "parsed",
                schoolId,
                schoolName,
                importerVersion,
                courses,
                warnings,
                null
        );
    }

    public static HtmlImportCourseResponse saved(String schoolName, String savedFileName) {
        return new HtmlImportCourseResponse(
                "saved",
                null,
                schoolName,
                null,
                List.of(),
                List.of(),
                savedFileName
        );
    }
}
