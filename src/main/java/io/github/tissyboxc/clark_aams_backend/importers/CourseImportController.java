package io.github.tissyboxc.clark_aams_backend.importers;

import io.github.tissyboxc.clark_aams_backend.common.ApiResponse;
import io.github.tissyboxc.clark_aams_backend.course.CourseService;
import io.github.tissyboxc.clark_aams_backend.importers.dto.HtmlImportCourseRequest;
import io.github.tissyboxc.clark_aams_backend.importers.dto.HtmlImportCourseResponse;
import io.github.tissyboxc.clark_aams_backend.importers.dto.ImportCourseRequest;
import io.github.tissyboxc.clark_aams_backend.importers.dto.ImportCourseResponse;
import io.github.tissyboxc.clark_aams_backend.pickup.CoursePickupCreateResponse;
import io.github.tissyboxc.clark_aams_backend.security.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 教务导入。
 *
 * <p>导入保持匿名可用（分享链接、免登录粘贴 HTML 都是有效场景），
 * 但**已登录用户的导入结果会顺带落库**（P3-1），
 * 使其在任意设备打开「我的课表」都能看到。
 * 未登录则维持原行为：只返回结果，不落库。
 */
@RestController
@RequestMapping(value = "/api/v1/imports", produces = "application/json;charset=UTF-8")
public class CourseImportController {
    private final CourseImportService courseImportService;
    private final CourseService courseService;
    private final CurrentUserResolver currentUserResolver;

    public CourseImportController(
            CourseImportService courseImportService,
            CourseService courseService,
            CurrentUserResolver currentUserResolver
    ) {
        this.courseImportService = courseImportService;
        this.courseService = courseService;
        this.currentUserResolver = currentUserResolver;
    }

    @PostMapping("/{schoolId}/courses")
    public ApiResponse<ImportCourseResponse> importCourses(
            @PathVariable String schoolId,
            @RequestBody ImportCourseRequest request,
            HttpServletRequest httpRequest
    ) {
        ImportCourseResponse response = courseImportService.importCourses(schoolId, request);
        persistIfLoggedIn(httpRequest, response);
        return ApiResponse.ok(response);
    }

    @PostMapping("/{schoolId}/courses/pickup")
    public ApiResponse<CoursePickupCreateResponse> importCoursesAndCreatePickup(
            @PathVariable String schoolId,
            @RequestBody ImportCourseRequest request,
            HttpServletRequest httpRequest
    ) {
        // 一次导入，同时拿到取件码结果与强类型课程，避免重复导入
        CourseImportService.ImportWithPickup result =
                courseImportService.importCoursesAndCreatePickupTyped(schoolId, request);
        Long userId = currentUserResolver.resolveUserId(httpRequest);
        if (userId != null && result.courses() != null) {
            courseService.replaceFromImport(userId, result.courses());
        }
        return ApiResponse.ok(result.pickup());
    }

    @PostMapping("/courses/html")
    public ApiResponse<HtmlImportCourseResponse> importCoursesByHtml(
            @RequestBody HtmlImportCourseRequest request,
            HttpServletRequest httpRequest
    ) {
        CourseImportService.HtmlImportResult result = courseImportService.importCoursesByHtml(request);
        HtmlImportCourseResponse response = result.response();
        // 只有真正完成解析（拿到课程列表）才落库；存 HTML 待人工处理的分支没有课程
        Long userId = currentUserResolver.resolveUserId(httpRequest);
        if (userId != null && response != null && response.courses() != null && !response.courses().isEmpty()) {
            courseService.replaceFromImport(userId, response.courses());
        }
        return new ApiResponse<>(0, result.message(), response);
    }

    /** 已登录则把导入结果存为该用户的课表；未登录静默跳过。 */
    private void persistIfLoggedIn(HttpServletRequest request, ImportCourseResponse response) {
        Long userId = currentUserResolver.resolveUserId(request);
        if (userId != null && response != null && response.courses() != null) {
            courseService.replaceFromImport(userId, response.courses());
        }
    }
}
