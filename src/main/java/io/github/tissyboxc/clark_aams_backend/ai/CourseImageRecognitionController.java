package io.github.tissyboxc.clark_aams_backend.ai;

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

    /** 每日识别次数上限，可用配置覆盖。 */
    static final String DAILY_LIMIT_PROPERTY = "${clark-aams.quota.recognize.daily-limit:20}";
    /** 短时限流：每分钟最多几次。 */
    static final String RATE_LIMIT_PROPERTY = "${clark-aams.quota.recognize.per-minute:5}";

    private final CourseImageRecognitionService recognitionService;
    private final CurrentUserResolver currentUserResolver;
    private final QuotaGuard quotaGuard;
    private final int dailyLimit;
    private final int perMinuteLimit;

    public CourseImageRecognitionController(
            CourseImageRecognitionService recognitionService,
            CurrentUserResolver currentUserResolver,
            QuotaGuard quotaGuard,
            @org.springframework.beans.factory.annotation.Value(DAILY_LIMIT_PROPERTY) int dailyLimit,
            @org.springframework.beans.factory.annotation.Value(RATE_LIMIT_PROPERTY) int perMinuteLimit
    ) {
        this.recognitionService = recognitionService;
        this.currentUserResolver = currentUserResolver;
        this.quotaGuard = quotaGuard;
        this.dailyLimit = dailyLimit;
        this.perMinuteLimit = perMinuteLimit;
    }

    @PostMapping(
            value = "/recognize",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = "application/json;charset=UTF-8"
    )
    public ResponseEntity<String> recognize(
            @RequestParam("images") List<MultipartFile> images,
            HttpServletRequest request
    ) {
        long userId = currentUserResolver.requireUserId(request);
        quotaGuard.acquireRate("recognize", userId, 60_000L, perMinuteLimit);
        quotaGuard.acquireDaily("recognize", userId, dailyLimit);

        int remaining = dailyLimit <= 0 ? -1 : Math.max(0, dailyLimit - quotaGuard.usedToday("recognize", userId));
        return ResponseEntity.ok()
                .header("Content-Type", "application/json;charset=UTF-8")
                .header("X-Quota-Remaining", String.valueOf(remaining))
                .body(recognitionService.recognize(images));
    }
}
