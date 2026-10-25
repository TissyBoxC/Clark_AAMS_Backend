package io.github.tissyboxc.clark_aams_backend.security;

import io.github.tissyboxc.clark_aams_backend.admin.AdminAuthController;
import io.github.tissyboxc.clark_aams_backend.common.ApiResponse;
import io.github.tissyboxc.clark_aams_backend.user.UserProfileDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一身份查询。
 *
 * <p>背景（审查文档 P1）：前端此前要靠「猜」来决定进哪个工作台——
 * 管理员页面各自调 `/api/v1/admin/auth/setup` 判断，用户页面靠
 * `/api/v1/user/me` 判断，两个入口互不知情。于是出现了
 * 「已登录管理员刷新 `/admin` 又被踢回登录页」这类问题。
 *
 * <p>本接口把「当前是谁、什么角色」集中回答一次，前端只需调一个接口
 * 就能决定跳转，无需知道后端有几套凭据。
 *
 * <p>返回的 {@code user} 不含登录码（凭据不应出现在高频读取的响应里）。
 */
@RestController
@RequestMapping(value = "/api/v1/auth", produces = "application/json;charset=UTF-8")
public class AuthIdentityController {

    private final CurrentUserResolver currentUserResolver;

    public AuthIdentityController(CurrentUserResolver currentUserResolver) {
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/me")
    public ApiResponse<IdentityResponse> me(HttpServletRequest request) {
        Long userId = currentUserResolver.resolveUserId(request);

        // P1-1：身份以 users 表的 role 为准（单一事实来源）。
        // 若该用户记录的 role 是 admin，即使没有管理员会话也应判为管理员——
        // 这正是「一套账号、按 role 分流」的意义。
        if (userId != null) {
            UserProfileDto user = currentUserResolver.requireUser(request);
            if ("admin".equalsIgnoreCase(user.role())) {
                return ApiResponse.ok(new IdentityResponse(
                        Role.ADMIN,
                        user.withoutLoginCode(),
                        true,
                        user.username()
                ));
            }
        }

        if (isAdminSession(request)) {
            // 管理员身份优先：同一浏览器可能先登录了用户再登管理员，
            // 此时以管理员为准，否则会显示「已登录但不是管理员」的矛盾状态。
            return ApiResponse.ok(new IdentityResponse(
                    Role.ADMIN,
                    null,
                    true,
                    adminUsername(request)
            ));
        }
        if (userId != null) {
            UserProfileDto user = currentUserResolver.requireUser(request);
            return ApiResponse.ok(new IdentityResponse(
                    Role.USER,
                    user.withoutLoginCode(),
                    true,
                    user.username()
            ));
        }
        return ApiResponse.ok(new IdentityResponse(Role.GUEST, null, false, ""));
    }

    private boolean isAdminSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null
                && Boolean.TRUE.equals(session.getAttribute(AdminAuthController.SESSION_KEY));
    }

    private String adminUsername(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return "";
        }
        Object value = session.getAttribute(AdminAuthController.SESSION_USERNAME_KEY);
        return value instanceof String text ? text : "";
    }

    /**
     * 身份响应。
     *
     * @param role         角色：guest / user / admin
     * @param user         用户资料（admin 或 guest 时为 null）
     * @param authenticated 是否已登录
     * @param displayName  展示名（管理员取账号名）
     */
    public record IdentityResponse(
            Role role,
            UserProfileDto user,
            boolean authenticated,
            String displayName
    ) {
    }
}
