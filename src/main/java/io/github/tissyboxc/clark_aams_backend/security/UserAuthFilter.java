package io.github.tissyboxc.clark_aams_backend.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 敏感接口的登录校验。
 *
 * <p>背景（审查条目 D1/D2/D6）：以下接口此前完全匿名可访问：
 * <ul>
 *   <li>{@code POST /api/v1/courses/recognize} —— 调用视觉大模型，按量计费</li>
 *   <li>{@code POST /api/v1/course-pickups} —— 上传图片并落盘</li>
 *   <li>{@code POST /api/v1/ai/questions/analyze} —— 调用文本大模型</li>
 *   <li>{@code POST /api/v1/ai/questions/analyze/fill-blank}</li>
 * </ul>
 * 任何一个被脚本刷到都会直接烧钱或撑爆磁盘。
 *
 * <p>只挡「创建/消耗型」的 POST；读取型接口（如按取件码取回课表）
 * 保持匿名，因为取件码本身就是凭据，且 Web 分享场景需要免登录访问。
 */
@Component
@Order(2)
public class UserAuthFilter implements Filter {

    private static final int UNAUTHORIZED = 40101;

    /** 需要登录的「方法 + 路径前缀」组合。 */
    private static final List<ProtectedRoute> PROTECTED = List.of(
            new ProtectedRoute("POST", "/api/v1/courses/recognize"),
            new ProtectedRoute("POST", "/api/v1/course-pickups"),
            // 原为 "/api/v1/ai/questions/"，与控制器实际路径
            // "/api/v1/questions" 不符，导致该过滤从未生效（P2 记录在案）。
            // 控制器内部另有 requireUserId 兜底，但过滤层不应形同虚设。
            new ProtectedRoute("POST", "/api/v1/questions/"),
            // 课表读写按用户隔离（P3-1）：读也要求登录，
            // 因为返回的是「我的」课表，匿名无从确定归属。
            new ProtectedRoute("GET", "/api/v1/courses/schedule"),
            new ProtectedRoute("PUT", "/api/v1/courses/schedule"),
            new ProtectedRoute("DELETE", "/api/v1/courses/schedule")
    );

    private final CurrentUserResolver currentUserResolver;

    public UserAuthFilter(CurrentUserResolver currentUserResolver) {
        this.currentUserResolver = currentUserResolver;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (CorsUtils.isPreFlightRequest(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        if (!isProtected(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        if (currentUserResolver.resolveUserId(httpRequest) != null) {
            chain.doFilter(request, response);
            return;
        }

        writeUnauthorized(httpRequest, httpResponse);
    }

    private boolean isProtected(HttpServletRequest request) {
        String path = normalizePath(request);
        String method = request.getMethod();
        for (ProtectedRoute route : PROTECTED) {
            if (!route.method().equalsIgnoreCase(method)) {
                continue;
            }
            if (route.prefix().endsWith("/")) {
                if (path.startsWith(route.prefix())) {
                    return true;
                }
            } else if (path.equals(route.prefix())) {
                return true;
            }
        }
        return false;
    }

    private String normalizePath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String uri = request.getRequestURI();
        if (contextPath == null || contextPath.isBlank()) {
            return uri;
        }
        return uri.substring(contextPath.length());
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank()) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Vary", "Origin");
            response.setHeader("Access-Control-Allow-Credentials", "true");
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"code\":" + UNAUTHORIZED + ",\"message\":\"请先登录\",\"data\":null}");
    }

    private record ProtectedRoute(String method, String prefix) {
    }
}
