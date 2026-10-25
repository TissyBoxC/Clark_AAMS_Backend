package io.github.tissyboxc.clark_aams_backend.importers.schools;

import io.github.tissyboxc.clark_aams_backend.importers.dto.ImportCourseRequest;
import io.github.tissyboxc.clark_aams_backend.importers.dto.ImportCourseResponse;
import io.github.tissyboxc.clark_aams_backend.school.SchoolEntity;

public interface SchoolAdapter {
    SchoolEntity school();

    ImportCourseResponse importCourses(ImportCourseRequest request);

    ImportCourseResponse importHtml(String html);

    default String schoolId() {
        return school().id();
    }
}
