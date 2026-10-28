package io.github.tissyboxc.clark_aams_backend.admin;

import io.github.tissyboxc.clark_aams_backend.common.ApiResponse;
import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import io.github.tissyboxc.clark_aams_backend.user.UserAccountService;
import io.github.tissyboxc.clark_aams_backend.user.UserController;
import io.github.tissyboxc.clark_aams_backend.user.UserProfileDto;
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
    private final UserAccountService userAccountService;

    public AdminAuthController(AdminCredentialStore credentialStore,
                               UserAccountService userAccountService) {
        this.credentialStore = credentialStore;
        this.userAccountService = userAccountService;
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
                    .body(ApiResponse.fail(ErrorCode.BAD_CREDENTIALS, "账号或密码错误"));
        }

        HttpSession session = httpRequest.getSession(true);
        establishAdminIdentity(session, request.username());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("authenticated", true)));
    }

    /**
     * 在一个会话里建立管理员身份（P1-1）。
     *
     * <p>管理员现在同时具备普通用户能力：登录时会在 users 表里确保有一条
     * role='admin' 的记录，并把它的 id 写进会话的用户标记。这样管理台和
     * 用户工作台可以共用一个会话，不必再登录两次。
     *
     * <p>之所以还保留独立的 {@link #SESSION_KEY}：管理员凭据由
     * {@link AdminCredentialStore} 校验，可能发生在用户记录建立之前，
     * 两个标记互为兜底（{@code AdminAuthFilter} 也同时认这两种）。
     */
    private void establishAdminIdentity(HttpSession session, String username) {
        session.setAttribute(SESSION_KEY, Boolean.TRUE);
        session.setAttribute(SESSION_USERNAME_KEY, username);

        UserProfileDto adminUser = userAccountService.ensureAdminUser(username);
        session.setAttribute(UserController.SESSION_KEY, adminUser.id());
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
