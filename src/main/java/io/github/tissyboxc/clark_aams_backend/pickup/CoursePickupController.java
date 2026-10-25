package io.github.tissyboxc.clark_aams_backend.pickup;

import io.github.tissyboxc.clark_aams_backend.common.ApiResponse;
import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import io.github.tissyboxc.clark_aams_backend.security.CurrentUserResolver;
import io.github.tissyboxc.clark_aams_backend.security.QuotaGuard;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 课表取件码。
 *
 * <p>创建取件码需要登录并受配额约束：该操作会调用识别并落盘图片
 * （审查条目 D2）。读取取件码保持匿名——取件码本身即凭据，
 * 且 Web 分享需要免登录打开（审查条目中确认按此设计）。
 */
@RestController
@RequestMapping("/api/v1/course-pickups")
public class CoursePickupController {

    /** 旧格式媒体类型：显式请求时返回裸数组，供未升级客户端使用（deprecated）。 */
    public static final String LEGACY_MEDIA_TYPE = "application/vnd.clark-aams.legacy+json";

    static final String DAILY_LIMIT_PROPERTY = "${clark-aams.quota.pickup.daily-limit:20}";
    static final String RATE_LIMIT_PROPERTY = "${clark-aams.quota.pickup.per-minute:5}";

    private final CoursePickupService pickupService;
    private final CoursePickupStore pickupStore;
    private final CurrentUserResolver currentUserResolver;
    private final QuotaGuard quotaGuard;
    private final int dailyLimit;
    private final int perMinuteLimit;

    public CoursePickupController(
            CoursePickupService pickupService,
            CoursePickupStore pickupStore,
            CurrentUserResolver currentUserResolver,
            QuotaGuard quotaGuard,
            @Value(DAILY_LIMIT_PROPERTY) int dailyLimit,
            @Value(RATE_LIMIT_PROPERTY) int perMinuteLimit
    ) {
        this.pickupService = pickupService;
        this.pickupStore = pickupStore;
        this.currentUserResolver = currentUserResolver;
        this.quotaGuard = quotaGuard;
        this.dailyLimit = dailyLimit;
        this.perMinuteLimit = perMinuteLimit;
    }

    @PostMapping(
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = "application/json;charset=UTF-8"
    )
    public ApiResponse<CoursePickupCreateResponse> create(
            @RequestParam("images") List<MultipartFile> images,
            HttpServletRequest request
    ) {
        long userId = currentUserResolver.requireUserId(request);
        quotaGuard.acquireRate("pickup", userId, 60_000L, perMinuteLimit);
        quotaGuard.acquireDaily("pickup", userId, dailyLimit);
        return ApiResponse.ok(pickupService.recognizeAndCreate(images));
    }

    /**
     * 按取件码取回课程（匿名可读）。
     *
     * <p>**契约变更（P2-2）**：此前直接返回存储的原始 JSON 数组，
     * 是全项目唯一的「裸 JSON」分支，客户端必须为它单独写一套解析。
     * 现在改为标准信封 `{code,message,data}`，`data` 为课程数组。
     *
     * <p>兼容策略：同一路径同时支持两种读法——
     * 客户端若在 `Accept` 中声明 `application/vnd.clark-aams.legacy+json`
     * 仍收到旧的裸数组。这样 App/Web 可以分开发版，不必强同步升级窗口。
     * 该兼容通道标记为 deprecated，P3 结束前移除。
     */
    @GetMapping(value = "/{code}", produces = {MediaType.APPLICATION_JSON_VALUE})
    public ApiResponse<Object> get(@PathVariable String code) {
        CoursePickupRecordDto record = pickupStore.find(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "取件码不存在"));
        return ApiResponse.ok(pickupService.parseCourses(record.jsonText()));
    }

    /**
     * 旧格式通道（deprecated）。
     *
     * <p>只在客户端显式请求 legacy 媒体类型时命中，
     * 保证已发布的 App v1.2.0 及更早版本仍能取件。
     */
    @GetMapping(value = "/{code}", produces = LEGACY_MEDIA_TYPE)
    public ResponseEntity<String> getLegacy(@PathVariable String code) {
        CoursePickupRecordDto record = pickupStore.find(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "取件码不存在"));
        return ResponseEntity.ok()
                .header("Content-Type", "application/json;charset=UTF-8")
                .header("Deprecation", "true")
                .header("Sunset", "Wed, 31 Dec 2026 23:59:59 GMT")
                .body(record.jsonText());
    }
}
