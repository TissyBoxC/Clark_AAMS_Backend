package io.github.tissyboxc.clark_aams_backend.ai;

import io.github.tissyboxc.clark_aams_backend.common.ApiResponse;
import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import io.github.tissyboxc.clark_aams_backend.user.UserAccountService;
import io.github.tissyboxc.clark_aams_backend.user.UserController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping(value = "/api/v1/questions", produces = "application/json;charset=UTF-8")
public class QuestionAnalysisController {
    private final QuestionAnalysisService analysisService;
    private final UserAccountService userAccountService;

    public QuestionAnalysisController(
            QuestionAnalysisService analysisService,
            UserAccountService userAccountService) {
        this.analysisService = analysisService;
        this.userAccountService = userAccountService;
    }

    @PostMapping("/analyze")
    public ApiResponse<Map<String, Object>> analyze(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        long userId = requireCurrentUserId(httpRequest);
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Request body is required");
        }
        Map<String, Object> result = analysisService.analyze(request);
        userAccountService.incrementQuestionAnalysisCount(userId);
        return ApiResponse.ok(result);
    }

    private long requireCurrentUserId(HttpServletRequest request) {
        Long id = currentUserId(request);
        if (id == null) {
            id = restoreUserIdFromLoginCodeHeader(request);
        }
        if (id == null) {
            throw new BusinessException(ErrorCode.LOGIN_INVALID, "请先登录后再使用做题辅助");
        }
        return id;
    }

    private Long restoreUserIdFromLoginCodeHeader(HttpServletRequest request) {
        String loginCode = header(request, "X-Clark-Aams-Login-Code");
        if (loginCode.isBlank()) {
            return null;
        }
        try {
            var user = userAccountService.getByLoginCode(loginCode);
            HttpSession session = request.getSession(true);
            session.setAttribute(UserController.SESSION_KEY, user.id());
            return user.id();
        } catch (BusinessException exception) {
            return null;
        }
    }

    private Long currentUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(UserController.SESSION_KEY);
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Integer intValue) {
            return intValue.longValue();
        }
        return null;
    }

    private String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value == null ? "" : value.trim();
    }
}
