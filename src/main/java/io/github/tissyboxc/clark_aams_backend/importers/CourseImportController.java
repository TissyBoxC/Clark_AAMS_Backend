package io.github.tissyboxc.clark_aams_backend.importers;

import io.github.tissyboxc.clark_aams_backend.common.ApiResponse;
import io.github.tissyboxc.clark_aams_backend.importers.dto.ImportCourseRequest;
import io.github.tissyboxc.clark_aams_backend.importers.dto.ImportCourseResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/imports", produces = "application/json;charset=UTF-8")
public class CourseImportController {
    private final CourseImportService courseImportService;

    public CourseImportController(CourseImportService courseImportService) {
        this.courseImportService = courseImportService;
    }

    @PostMapping("/{schoolId}/courses")
    public ApiResponse<ImportCourseResponse> importCourses(
            @PathVariable String schoolId,
            @RequestBody ImportCourseRequest request
    ) {
        return ApiResponse.ok(courseImportService.importCourses(schoolId, request));
    }
}
