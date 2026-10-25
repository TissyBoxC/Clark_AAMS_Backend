package io.github.tissyboxc.clark_aams_backend.admin;

import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import io.github.tissyboxc.clark_aams_backend.user.UserController;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Order(1)
public class AdminAuthFilter implements Filter {
    private static final String ADMIN_AUTH_API_PREFIX = "/api/v1/admin/auth/";
    private static final String ADMIN_API_PREFIX = "/api/v1/admin/";

    private final AdminCredentialStore credentialStore;

    public AdminAuthFilter(AdminCredentialStore credentialStore) {
        this.credentialStore = credentialStore;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String path = normalizePath(httpRequest);

        if (CorsUtils.isPreFlightRequest(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        if (!isProtectedAdminApi(path)) {
            chain.doFilter(request, response);
            return;
        }

        if (!credentialStore.configured()) {
            writeUnauthorizedJson(httpRequest, httpResponse, 40102, "后台管理员尚未初始化");
            return;
        }

        if (isAuthenticated(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        // 已登录的普通用户访问后台 → 403（而不是 401）。
        // 401 会诱导客户端反复重试登录，403 才是「你没有这个身份」的准确回答。
        // 对应审查条目 P1-3：role=user 访问 /admin/** 应得 403。
        if (isRegularUser(httpRequest)) {
            writeForbiddenJson(httpRequest, httpResponse);
            return;
        }

        writeUnauthorizedJson(httpRequest, httpResponse, 40101, "请先登录后台管理");
    }

    /** 是否存在有效的普通用户会话（与 `CurrentUserResolver` 的判据一致）。 */
    private boolean isRegularUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute(UserController.SESSION_KEY) != null;
    }

    private boolean isProtectedAdminApi(String path) {
        return path.startsWith(ADMIN_API_PREFIX) && !path.startsWith(ADMIN_AUTH_API_PREFIX);
    }

    private boolean isAuthenticated(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && Boolean.TRUE.equals(session.getAttribute(AdminAuthController.SESSION_KEY));
    }

    private String normalizePath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String uri = request.getRequestURI();
        if (contextPath == null || contextPath.isBlank()) {
            return uri;
        }
        return uri.substring(contextPath.length());
    }

    private void writeUnauthorizedJson(
            HttpServletRequest request,
            HttpServletResponse response,
            int code,
            String message
    ) throws IOException {
        writeJson(request, response, HttpServletResponse.SC_UNAUTHORIZED, code, message);
    }

    private void writeForbiddenJson(HttpServletRequest request, HttpServletResponse response) throws IOException {
        writeJson(
                request,
                response,
                HttpServletResponse.SC_FORBIDDEN,
                ErrorCode.FORBIDDEN.code(),
                "当前账号没有后台管理权限"
        );
    }

    private void writeJson(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            int code,
            String message
    ) throws IOException {
        addCorsHeadersIfNeeded(request, response);
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + code + ",\"message\":\"" + message + "\",\"data\":null}");
    }

    private void addCorsHeadersIfNeeded(HttpServletRequest request, HttpServletResponse response) {
        String origin = response.getHeader("Access-Control-Allow-Origin");
        if (origin != null && !origin.isBlank()) {
            return;
        }
        String requestOrigin = request.getHeader("Origin");
        if (requestOrigin == null || requestOrigin.isBlank()) {
            return;
        }
        response.setHeader("Access-Control-Allow-Origin", requestOrigin);
        response.setHeader("Vary", "Origin");
        response.setHeader("Access-Control-Allow-Credentials", "true");
    }
}
