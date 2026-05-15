package io.github.tissyboxc.clark_aams_backend.admin;

import io.github.tissyboxc.clark_aams_backend.common.ApiResponse;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping(value = "/api/v1/admin/auth", produces = "application/json;charset=UTF-8")
public class AdminAuthController {
    static final String SESSION_KEY = "CLARK_AAMS_ADMIN_AUTHENTICATED";
    private final String adminUsername;
    private final String adminPassword;

    public AdminAuthController(
            @Value("${clark-aams.admin.username:}") String adminUsername,
            @Value("${clark-aams.admin.password:}") String adminPassword
    ) {
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> login(
            @RequestBody AdminLoginRequest request,
            HttpServletRequest httpRequest
    ) {
        if (!credentialsConfigured()
                || !adminUsername.equals(request.username())
                || !adminPassword.equals(request.password())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.fail(ErrorCode.LOGIN_INVALID, "账号或密码错误"));
        }

        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(SESSION_KEY, Boolean.TRUE);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("authenticated", true)));
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

    private boolean credentialsConfigured() {
        return adminUsername != null && !adminUsername.isBlank()
                && adminPassword != null && !adminPassword.isBlank();
    }
}
