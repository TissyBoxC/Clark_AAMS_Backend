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

    public UserController(UserAccountService userAccountService, EmailCodeSender emailCodeSender) {
        this.userAccountService = userAccountService;
        this.emailCodeSender = emailCodeSender;
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
        return ApiResponse.ok(currentUser(request));
    }

    @PostMapping("/email/code")
    public ApiResponse<UserProfileDto> requestEmailCode(@RequestBody UserEmailCodeRequest request, HttpServletRequest httpRequest) {
        UserAccountService.EmailChallenge challenge = userAccountService.requestEmailChallenge(requireCurrentUserId(httpRequest), request.qqEmail());
        emailCodeSender.sendVerificationCode(challenge.user().email(), challenge.code());
        return ApiResponse.ok(challenge.user());
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
            throw new BusinessException(ErrorCode.LOGIN_INVALID, "请先登录");
        }
        return id;
    }
}
