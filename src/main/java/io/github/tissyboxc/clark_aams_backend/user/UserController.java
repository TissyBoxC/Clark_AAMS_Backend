package io.github.tissyboxc.clark_aams_backend.user;

import io.github.tissyboxc.clark_aams_backend.common.ApiResponse;
import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping(value = "/api/v1/user", produces = "application/json;charset=UTF-8")
public class UserController {
    public static final String SESSION_KEY = "CLARK_AAMS_USER_ID";

    private final UserAccountService userAccountService;
    private final EmailCodeSender emailCodeSender;
    private final EmailCodeCooldownService emailCodeCooldownService;

    public UserController(
            UserAccountService userAccountService,
            EmailCodeSender emailCodeSender,
            EmailCodeCooldownService emailCodeCooldownService
    ) {
        this.userAccountService = userAccountService;
        this.emailCodeSender = emailCodeSender;
        this.emailCodeCooldownService = emailCodeCooldownService;
    }

    @PostMapping("/register")
    public ApiResponse<UserProfileDto> register(@RequestBody UserRegisterRequest request, HttpServletRequest httpRequest) {
        UserProfileDto user = userAccountService.register(request);
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(SESSION_KEY, user.id());
        return ApiResponse.ok(user);
    }

    @PostMapping("/login")
    public ApiResponse<UserLoginResponse> login(@RequestBody UserLoginRequest request, HttpServletRequest httpRequest) {
        UserProfileDto user = userAccountService.login(request);
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(SESSION_KEY, user.id());
        return ApiResponse.ok(new UserLoginResponse(
                user.id(),
                user.username(),
                user.email(),
                user.loginCode(),
                user.emailBound(),
                user.emailVerified(),
                user.questionAnalysisCount()
        ));
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, Boolean>> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(SESSION_KEY);
        }
        return ApiResponse.ok(Map.of("authenticated", false));
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileDto> me(HttpServletRequest request) {
        // 不带登录码：该接口会被频繁调用，凭据不应出现在响应里（F4）
        return ApiResponse.ok(currentUser(request).withoutLoginCode());
    }

    /**
     * 退出所有设备：吊销当前登录码并签发新的。
     *
     * <p>调用方若已登录，会在响应里拿到**新**登录码，需要立即替换本地缓存；
     * 旧码从现在起失效，其他设备下次请求即被拒绝（A2）。
     */
    @PostMapping("/login-code/rotate")
    public ApiResponse<UserProfileDto> rotateLoginCode(HttpServletRequest request) {
        long userId = requireCurrentUserId(request);
        UserProfileDto rotated = userAccountService.rotateLoginCode(userId);
        return ApiResponse.ok(rotated);
    }

    @PostMapping("/email/code")
    public ApiResponse<UserProfileDto> requestEmailCode(@RequestBody UserEmailCodeRequest request, HttpServletRequest httpRequest) {
        long userId = requireCurrentUserId(httpRequest);
        EmailCodeCooldownService.CooldownTicket cooldownTicket = emailCodeCooldownService.reserve(userId);
        try {
            UserAccountService.EmailChallenge challenge =
                    userAccountService.requestEmailChallenge(userId, request.qqEmail());
            emailCodeSender.sendVerificationCode(challenge.user().email(), challenge.code());
            return ApiResponse.ok(challenge.user());
        } catch (RuntimeException exception) {
            emailCodeCooldownService.release(cooldownTicket);
            throw exception;
        }
    }

    @PostMapping("/email/verify")
    public ApiResponse<UserProfileDto> verifyEmail(@RequestBody UserEmailVerifyRequest request, HttpServletRequest httpRequest) {
        UserProfileDto user = userAccountService.confirmEmail(requireCurrentUserId(httpRequest), request.code());
        return ApiResponse.ok(user);
    }

    private UserProfileDto currentUser(HttpServletRequest request) {
        Long id = currentUserId(request);
        if (id == null) {
            id = restoreUserIdFromLoginCodeHeader(request);
        }
        if (id == null) {
            return new UserProfileDto(0, "", "", "", false, false, 0);
        }
        return userAccountService.getById(id);
    }

    private Long restoreUserIdFromLoginCodeHeader(HttpServletRequest request) {
        String loginCode = header(request, "X-Clark-Aams-Login-Code");
        if (loginCode.isBlank()) {
            return null;
        }
        try {
            UserProfileDto user = userAccountService.getByLoginCode(loginCode);
            HttpSession session = request.getSession(true);
            session.setAttribute(SESSION_KEY, user.id());
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
        Object value = session.getAttribute(SESSION_KEY);
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

    private long requireCurrentUserId(HttpServletRequest request) {
        Long id = currentUserId(request);
        if (id == null) {
            id = restoreUserIdFromLoginCodeHeader(request);
        }
        if (id == null) {
            throw new BusinessException(ErrorCode.LOGIN_INVALID, "请先登录");
        }
        return id;
    }
}
