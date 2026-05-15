package io.github.tissyboxc.clark_aams_backend.importers;

import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import io.github.tissyboxc.clark_aams_backend.importers.dto.ImportCourseRequest;
import io.github.tissyboxc.clark_aams_backend.importers.dto.ImportCourseResponse;
import org.springframework.stereotype.Service;

@Service
public class CourseImportService {
    private final ImporterRegistry importerRegistry;

    public CourseImportService(ImporterRegistry importerRegistry) {
        this.importerRegistry = importerRegistry;
    }

    public ImportCourseResponse importCourses(String schoolId, ImportCourseRequest request) {
        if (schoolId == null || schoolId.isBlank() || request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        return importerRegistry.get(schoolId).importCourses(request);
    }
}
