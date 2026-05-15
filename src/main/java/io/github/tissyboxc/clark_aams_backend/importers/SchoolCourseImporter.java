package io.github.tissyboxc.clark_aams_backend.importers;

import io.github.tissyboxc.clark_aams_backend.importers.dto.ImportCourseRequest;
import io.github.tissyboxc.clark_aams_backend.importers.dto.ImportCourseResponse;

public interface SchoolCourseImporter {
    String schoolId();

    ImportCourseResponse importCourses(ImportCourseRequest request);
}
