package io.github.tissyboxc.clark_aams_backend.admin;

import io.github.tissyboxc.clark_aams_backend.common.ApiResponse;
import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import io.github.tissyboxc.clark_aams_backend.user.UserController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping(value = "/api/v1/admin/auth", produces = "application/json;charset=UTF-8")
public class AdminAuthController {
    public static final String SESSION_KEY = "CLARK_AAMS_ADMIN_AUTHENTICATED";
    /** 当前管理员账号名，供 `/auth/me` 展示；不存密码或哈希。 */
    public static final String SESSION_USERNAME_KEY = "CLARK_AAMS_ADMIN_USERNAME";
    private final AdminCredentialStore credentialStore;

    public AdminAuthController(AdminCredentialStore credentialStore) {
        this.credentialStore = credentialStore;
    }

    @GetMapping("/setup")
    public ApiResponse<AdminSetupStatusDto> setupStatus() {
        return ApiResponse.ok(new AdminSetupStatusDto(
                credentialStore.configured(),
                credentialStore.registrationAvailable()
        ));
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Boolean>> register(
            @RequestBody AdminRegisterRequest request,
            HttpServletRequest httpRequest
    ) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        credentialStore.register(request.username(), request.password());
        HttpSession session = httpRequest.getSession(true);
        establishAdminIdentity(session, request.username());
        return ApiResponse.ok(Map.of("authenticated", true));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> login(
            @RequestBody AdminLoginRequest request,
            HttpServletRequest httpRequest
    ) {
        if (request == null || !credentialStore.authenticate(request.username(), request.password())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.fail(ErrorCode.LOGIN_INVALID, "账号或密码错误"));
        }

        HttpSession session = httpRequest.getSession(true);
        establishAdminIdentity(session, request.username());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("authenticated", true)));
    }

    /**
     * 在一个会话里建立管理员身份（P1-1）。
     *
     * <p>与 `UserController#establishUserIdentity` 对称：设置管理员标记时
     * 必须清掉普通用户标记，否则两种身份会共存在同一会话里，
     * 导致「先登录用户、再登录管理员」后身份判定出现歧义。
     */
    private void establishAdminIdentity(HttpSession session, String username) {
        session.setAttribute(SESSION_KEY, Boolean.TRUE);
        session.setAttribute(SESSION_USERNAME_KEY, username);
        session.removeAttribute(UserController.SESSION_KEY);
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, Boolean>> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ApiResponse.ok(Map.of("authenticated", false));
    }

    public record AdminLoginRequest(String username, String password) {
    }
}
