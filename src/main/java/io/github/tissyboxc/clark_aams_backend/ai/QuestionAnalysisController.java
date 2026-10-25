package io.github.tissyboxc.clark_aams_backend.ai;

import io.github.tissyboxc.clark_aams_backend.common.ApiResponse;
import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import io.github.tissyboxc.clark_aams_backend.security.CurrentUserResolver;
import io.github.tissyboxc.clark_aams_backend.security.QuotaGuard;
import io.github.tissyboxc.clark_aams_backend.user.UserAccountService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 题目解析（做题辅助）。
 *
 * <p>调用文本大模型，按量计费。此前虽然要求登录，但没有任何配额
 * （审查条目 D6），单账号可持续刷。现在加每日上限 + 短时限流。
 *
 * <p>同时把原先私有的 session/登录码解析逻辑收敛到
 * {@link CurrentUserResolver}，避免三处各写一份、行为不一致。
 */
@RestController
@RequestMapping(value = "/api/v1/questions", produces = "application/json;charset=UTF-8")
public class QuestionAnalysisController {

    static final String DAILY_LIMIT_PROPERTY = "${clark-aams.quota.analyze.daily-limit:50}";
    static final String RATE_LIMIT_PROPERTY = "${clark-aams.quota.analyze.per-minute:10}";

    private final QuestionAnalysisService analysisService;
    private final UserAccountService userAccountService;
    private final CurrentUserResolver currentUserResolver;
    private final QuotaGuard quotaGuard;
    private final int dailyLimit;
    private final int perMinuteLimit;

    public QuestionAnalysisController(
            QuestionAnalysisService analysisService,
            UserAccountService userAccountService,
            CurrentUserResolver currentUserResolver,
            QuotaGuard quotaGuard,
            @Value(DAILY_LIMIT_PROPERTY) int dailyLimit,
            @Value(RATE_LIMIT_PROPERTY) int perMinuteLimit
    ) {
        this.analysisService = analysisService;
        this.userAccountService = userAccountService;
        this.currentUserResolver = currentUserResolver;
        this.quotaGuard = quotaGuard;
        this.dailyLimit = dailyLimit;
        this.perMinuteLimit = perMinuteLimit;
    }

    @PostMapping("/analyze")
    public ApiResponse<Map<String, Object>> analyze(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        long userId = requireQuota(httpRequest);
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Request body is required");
        }
        Map<String, Object> result = analysisService.analyze(request);
        userAccountService.incrementQuestionAnalysisCount(userId);
        return ApiResponse.ok(result);
    }

    @PostMapping("/analyze/fill-blank")
    public ApiResponse<Map<String, Object>> analyzeFillBlank(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        long userId = requireQuota(httpRequest);
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Request body is required");
        }
        Map<String, Object> result = analysisService.analyzeFillBlank(request);
        userAccountService.incrementQuestionAnalysisCount(userId);
        return ApiResponse.ok(result);
    }

    /** 校验登录并占用配额；未登录抛 401，超限抛 429。 */
    private long requireQuota(HttpServletRequest request) {
        long userId = currentUserResolver.requireUserId(request);
        quotaGuard.acquireRate("analyze", userId, 60_000L, perMinuteLimit);
        quotaGuard.acquireDaily("analyze", userId, dailyLimit);
        return userId;
    }
}
