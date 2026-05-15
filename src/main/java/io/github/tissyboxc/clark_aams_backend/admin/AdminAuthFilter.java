package io.github.tissyboxc.clark_aams_backend.admin;

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

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@Order(1)
public class AdminAuthFilter implements Filter {
    private static final String LOGIN_PAGE = "/admin/login.html";
    private static final String REGISTER_PAGE = "/admin/register.html";
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

        if (!credentialStore.configured() && requiresSetupRedirect(path)) {
            httpResponse.sendRedirect(REGISTER_PAGE);
            return;
        }

        if (!requiresAuthentication(path) || isAuthenticated(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        if (isAdminApi(path)) {
            writeUnauthorizedJson(httpResponse);
            return;
        }

        httpResponse.sendRedirect(LOGIN_PAGE + "?redirect=" + encodeRedirect(httpRequest));
    }

    private boolean requiresAuthentication(String path) {
        if (path.equals(LOGIN_PAGE)
                || path.equals(REGISTER_PAGE)
                || path.startsWith(ADMIN_AUTH_API_PREFIX)) {
            return false;
        }
        return path.equals("/admin")
                || path.equals("/admin/")
                || path.startsWith("/admin/")
                || isAdminApi(path);
    }

    private boolean isAdminApi(String path) {
        return path.startsWith(ADMIN_API_PREFIX) && !path.startsWith(ADMIN_AUTH_API_PREFIX);
    }

    private boolean requiresSetupRedirect(String path) {
        if (path.equals(REGISTER_PAGE) || path.startsWith(ADMIN_AUTH_API_PREFIX)) {
            return false;
        }
        return path.equals(LOGIN_PAGE)
                || path.equals("/admin")
                || path.equals("/admin/")
                || path.startsWith("/admin/")
                || isAdminApi(path);
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

    private String encodeRedirect(HttpServletRequest request) {
        String target = normalizePath(request);
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            target += "?" + request.getQueryString();
        }
        return URLEncoder.encode(target, StandardCharsets.UTF_8);
    }

    private void writeUnauthorizedJson(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":40101,\"message\":\"请先登录后台管理\",\"data\":null}");
    }
}
