package io.github.tissyboxc.clark_aams_backend.course;

import io.github.tissyboxc.clark_aams_backend.common.ApiResponse;
import io.github.tissyboxc.clark_aams_backend.importers.dto.CourseDto;
import io.github.tissyboxc.clark_aams_backend.security.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 课表资源（P3-1）。
 *
 * <p>此前课表只存在于客户端，换设备即丢失，Web 与 App 也无法共享。
 * 本控制器把「我的课表」变成服务端资源，按登录用户隔离。
 *
 * <p>所有端点都要求登录：返回的是「我的」课表，
 * 匿名请求无从确定归属，因此直接 401（由 {@code UserAuthFilter} 拦截）。
 *
 * <p>写接口的请求体用 {@link CourseUpsertRequest} 而非 {@link CourseDto}：
 * 后者含 {@code List<int[]>} 与派生字段，既不便反序列化，
 * 也容易让客户端提交自相矛盾的周次。
 */
@RestController
@RequestMapping(value = "/api/v1/courses/schedule", produces = MediaType.APPLICATION_JSON_VALUE)
public class CourseController {

    private final CourseService courseService;
    private final CurrentUserResolver currentUserResolver;

    public CourseController(CourseService courseService, CurrentUserResolver currentUserResolver) {
        this.courseService = courseService;
        this.currentUserResolver = currentUserResolver;
    }

    /** 我的课表。 */
    @GetMapping
    public ApiResponse<List<CourseDto>> list(HttpServletRequest request) {
        return ApiResponse.ok(courseService.list(currentUserResolver.requireUserId(request)));
    }

    /** 用一组课程整体覆盖我的课表（导入后同步、或批量编辑时使用）。 */
    @PutMapping
    public ApiResponse<List<CourseDto>> replace(
            @RequestBody List<CourseUpsertRequest> courses,
            HttpServletRequest request
    ) {
        long userId = currentUserResolver.requireUserId(request);
        List<CourseDto> mapped = new ArrayList<>();
        if (courses != null) {
            for (CourseUpsertRequest course : courses) {
                if (course != null) {
                    mapped.add(course.toCourseDto());
                }
            }
        }
        return ApiResponse.ok(courseService.replaceFromImport(userId, mapped));
    }

    /** 清空我的课表。 */
    @DeleteMapping
    public ApiResponse<Map<String, Object>> clear(HttpServletRequest request) {
        int removed = courseService.clear(currentUserResolver.requireUserId(request));
        return ApiResponse.ok(Map.of("removed", removed));
    }

    /** 手工新增一门课。 */
    @PostMapping
    public ApiResponse<CourseDto> create(
            @RequestBody CourseUpsertRequest course,
            HttpServletRequest request
    ) {
        long userId = currentUserResolver.requireUserId(request);
        return ApiResponse.ok(courseService.create(userId, course.toCourseDto()));
    }

    /** 手工修改一门课。 */
    @PutMapping("/{courseId}")
    public ApiResponse<CourseDto> update(
            @PathVariable long courseId,
            @RequestBody CourseUpsertRequest course,
            HttpServletRequest request
    ) {
        long userId = currentUserResolver.requireUserId(request);
        return ApiResponse.ok(courseService.update(userId, courseId, course.toCourseDto()));
    }

    /** 删除一门课。 */
    @DeleteMapping("/{courseId}")
    public ApiResponse<Map<String, Object>> delete(
            @PathVariable long courseId,
            HttpServletRequest request
    ) {
        courseService.delete(currentUserResolver.requireUserId(request), courseId);
        return ApiResponse.ok(Map.of("deleted", courseId));
    }
}
