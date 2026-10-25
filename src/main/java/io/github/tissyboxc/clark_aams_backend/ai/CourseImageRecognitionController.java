package io.github.tissyboxc.clark_aams_backend.ai;

import io.github.tissyboxc.clark_aams_backend.common.ApiResponse;
import io.github.tissyboxc.clark_aams_backend.pickup.CoursePickupService;
import io.github.tissyboxc.clark_aams_backend.security.CurrentUserResolver;
import io.github.tissyboxc.clark_aams_backend.security.QuotaGuard;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 课表图片识别。
 *
 * <p>该接口会调用视觉大模型，按量计费，因此必须登录并受配额约束
 * （审查条目 D1）。登录校验在 {@code UserAuthFilter} 完成，
 * 这里只负责扣配额。
 */
@RestController
@RequestMapping("/api/v1/courses")
public class CourseImageRecognitionController {

    /** 旧格式媒体类型：显式请求时返回裸数组，供未升级客户端使用（deprecated）。 */
    static final String LEGACY_MEDIA_TYPE = "application/vnd.clark-aams.legacy+json";

    /** 每日识别次数上限，可用配置覆盖。 */
    static final String DAILY_LIMIT_PROPERTY = "${clark-aams.quota.recognize.daily-limit:20}";
    /** 短时限流：每分钟最多几次。 */
    static final String RATE_LIMIT_PROPERTY = "${clark-aams.quota.recognize.per-minute:5}";

    private final CourseImageRecognitionService recognitionService;
    private final CoursePickupService pickupService;
    private final CurrentUserResolver currentUserResolver;
    private final QuotaGuard quotaGuard;
    private final int dailyLimit;
    private final int perMinuteLimit;

    public CourseImageRecognitionController(
            CourseImageRecognitionService recognitionService,
            CoursePickupService pickupService,
            CurrentUserResolver currentUserResolver,
            QuotaGuard quotaGuard,
            @org.springframework.beans.factory.annotation.Value(DAILY_LIMIT_PROPERTY) int dailyLimit,
            @org.springframework.beans.factory.annotation.Value(RATE_LIMIT_PROPERTY) int perMinuteLimit
    ) {
        this.recognitionService = recognitionService;
        this.pickupService = pickupService;
        this.currentUserResolver = currentUserResolver;
        this.quotaGuard = quotaGuard;
        this.dailyLimit = dailyLimit;
        this.perMinuteLimit = perMinuteLimit;
    }

    /**
     * 课程图片识别。
     *
     * <p>**契约变更（P2-2）**：此前返回裸 JSON 数组，是需要客户端特殊处理的分支。
     * 现在统一为 `{code,message,data}` 信封，`data` 仍是课程数组；
     * 响应头 `X-Quota-Remaining` 保留，用于告知当日剩余识别次数。
     *
     * <p>兼容：显式请求 `application/vnd.clark-aams.legacy+json` 仍得到裸数组。
     */
    @PostMapping(
            value = "/recognize",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = {MediaType.APPLICATION_JSON_VALUE}
    )
    public ResponseEntity<ApiResponse<Object>> recognize(
            @RequestParam("images") List<MultipartFile> images,
            HttpServletRequest request
    ) {
        RecognitionOutcome outcome = recognizeInternal(images, request);
        return ResponseEntity.ok()
                .header("X-Quota-Remaining", String.valueOf(outcome.remaining()))
                .body(ApiResponse.ok(outcome.courses()));
    }

    /** 旧格式通道（deprecated），供未升级的前端版本使用。 */
    @PostMapping(
            value = "/recognize",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = {LEGACY_MEDIA_TYPE}
    )
    public ResponseEntity<String> recognizeLegacy(
            @RequestParam("images") List<MultipartFile> images,
            HttpServletRequest request
    ) {
        RecognitionOutcome outcome = recognizeInternal(images, request);
        return ResponseEntity.ok()
                .header("Content-Type", "application/json;charset=UTF-8")
                .header("X-Quota-Remaining", String.valueOf(outcome.remaining()))
                .header("Deprecation", "true")
                .body(outcome.jsonText());
    }

    private RecognitionOutcome recognizeInternal(List<MultipartFile> images, HttpServletRequest request) {
        long userId = currentUserResolver.requireUserId(request);
        quotaGuard.acquireRate("recognize", userId, 60_000L, perMinuteLimit);
        quotaGuard.acquireDaily("recognize", userId, dailyLimit);

        int remaining = dailyLimit <= 0
                ? -1
                : Math.max(0, dailyLimit - quotaGuard.usedToday("recognize", userId));
        String jsonText = recognitionService.recognize(images);
        return new RecognitionOutcome(pickupService.parseCourses(jsonText), jsonText, remaining);
    }

    private record RecognitionOutcome(Object courses, String jsonText, int remaining) {
    }
}
