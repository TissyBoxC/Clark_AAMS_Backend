package io.github.tissyboxc.clark_aams_backend.importers;

import io.github.tissyboxc.clark_aams_backend.common.ApiResponse;
import io.github.tissyboxc.clark_aams_backend.importers.dto.HtmlImportCourseRequest;
import io.github.tissyboxc.clark_aams_backend.importers.dto.HtmlImportCourseResponse;
import io.github.tissyboxc.clark_aams_backend.importers.dto.ImportCourseRequest;
import io.github.tissyboxc.clark_aams_backend.importers.dto.ImportCourseResponse;
import io.github.tissyboxc.clark_aams_backend.pickup.CoursePickupCreateResponse;
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

    @PostMapping("/{schoolId}/courses/pickup")
    public ApiResponse<CoursePickupCreateResponse> importCoursesAndCreatePickup(
            @PathVariable String schoolId,
            @RequestBody ImportCourseRequest request
    ) {
        return ApiResponse.ok(courseImportService.importCoursesAndCreatePickup(schoolId, request));
    }

    @PostMapping("/courses/html")
    public ApiResponse<HtmlImportCourseResponse> importCoursesByHtml(@RequestBody HtmlImportCourseRequest request) {
        CourseImportService.HtmlImportResult result = courseImportService.importCoursesByHtml(request);
        return new ApiResponse<>(0, result.message(), result.response());
    }
}
