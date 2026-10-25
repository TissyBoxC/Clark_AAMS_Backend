package io.github.tissyboxc.clark_aams_backend.importers.dto;

public record HtmlImportCourseRequest(
        String school,
        String schoolName,
        String html
) {
}
