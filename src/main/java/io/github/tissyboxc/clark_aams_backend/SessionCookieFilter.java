package io.github.tissyboxc.clark_aams_backend;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

@Component
@Order(0)
class SessionCookieFilter extends OncePerRequestFilter {
    private static final String SET_COOKIE = "Set-Cookie";
    private static final String SESSION_COOKIE_NAME = "JSESSIONID=";

    /**
     * 会话和 Cookie 的有效期，秒。
     *
     * <p>跟 {@code server.servlet.session.timeout} 保持一致，
     * 客户端和服务端的过期时间不同步会出「一方还认、一方已丢」的错位。
     */
    private final long maxAgeSeconds;

    SessionCookieFilter(
            @Value("${server.servlet.session.timeout:30d}") Duration sessionTimeout
    ) {
        this.maxAgeSeconds = Math.max(60, sessionTimeout.toSeconds());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        HttpServletResponseWrapper responseWrapper = new HttpServletResponseWrapper(response) {
            @Override
            public void addCookie(Cookie cookie) {
                if (cookie == null || !"JSESSIONID".equals(cookie.getName())) {
                    super.addCookie(cookie);
                    return;
                }
                // 会话 Cookie 统一走重写，同源跨站都补上 Max-Age，
                // 不能让任何一条路径漏掉，否则登录还是保持不住。
                super.addHeader(SET_COOKIE, buildSessionCookie(cookie, request));
            }

            @Override
            public void addHeader(String name, String value) {
                super.addHeader(name, rewriteSetCookie(name, value, request));
            }

            @Override
            public void setHeader(String name, String value) {
                super.setHeader(name, rewriteSetCookie(name, value, request));
            }
        };
        filterChain.doFilter(request, responseWrapper);

        /*
         * 会话仍在时补一条带 Max-Age 的 Cookie，让浏览器端的过期时间
         * 跟着一起往后滑。放在链路之后做，是为了拿到本次请求新建/续期
         * 之后的 session id；且只在还没写过 JSESSIONID 时补，避免重复。
         */
        if (!responseContainsSessionCookie(responseWrapper)) {
            refreshSessionCookie(request, responseWrapper);
        }
    }

    /** 本次响应是不是已经带过 JSESSIONID 了。 */
    private boolean responseContainsSessionCookie(HttpServletResponse response) {
        for (String header : response.getHeaders(SET_COOKIE)) {
            if (header != null && header.startsWith(SESSION_COOKIE_NAME)) {
                return true;
            }
        }
        return false;
    }

    private String rewriteSetCookie(String headerName, String headerValue, HttpServletRequest request) {
        if (!SET_COOKIE.equalsIgnoreCase(headerName) || headerValue == null || !headerValue.startsWith(SESSION_COOKIE_NAME)) {
            return headerValue;
        }

        String rewritten = headerValue;

        /*
         * 补 Max-Age。没有它浏览器会当成会话 Cookie，一关就丢——
         * 这正是"每次重开浏览器都要重新登录"的原因。
         * 同源和跨站都要补，所以放在这里（两条路径都会经过）。
         */
        if (!containsAttribute(rewritten, "Max-Age=") && !containsAttribute(rewritten, "Expires=")) {
            rewritten += "; Max-Age=" + maxAgeSeconds;
        }
        if (!containsAttribute(rewritten, "HttpOnly")) {
            rewritten += "; HttpOnly";
        }

        if (!isCrossSiteRequest(request) || !isHttpsRequest(request)) {
            return rewritten;
        }
        if (!containsAttribute(rewritten, "SameSite=")) {
            rewritten += "; SameSite=None";
        }
        if (!containsAttribute(rewritten, "Secure")) {
            rewritten += "; Secure";
        }
        return rewritten;
    }

    /**
     * 每次带会话访问都把有效期往后推，这样持续使用就一直不掉线。
     *
     * <p>Spring 的会话超时是从「最后一次请求」算的，正常访问本来就会续期。
     * 这里额外做一件事：如果响应里没带 Set-Cookie（多数请求都不会带），
     * 主动补一条带 Max-Age 的会话 Cookie，让浏览器端的过期时间也跟着滑动，
     * 不至于出现「服务端还认、浏览器已经丢了 Cookie」。
     */
    private void refreshSessionCookie(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        String id = session.getId();
        if (id == null || id.isBlank()) {
            return;
        }

        StringBuilder header = new StringBuilder();
        header.append("JSESSIONID=").append(id)
                .append("; Path=").append(contextPathOf(request))
                .append("; Max-Age=").append(maxAgeSeconds)
                .append("; HttpOnly");
        if (isHttpsRequest(request)) {
            header.append("; Secure");
        }
        if (isCrossSiteRequest(request)) {
            header.append("; SameSite=None");
        } else {
            header.append("; SameSite=Lax");
        }
        response.addHeader(SET_COOKIE, header.toString());
    }

    private String contextPathOf(HttpServletRequest request) {
        String path = request.getContextPath();
        return path == null || path.isBlank() ? "/" : path;
    }

    /**
     * 拼出会话 Cookie，同源和跨站共用。
     *
     * <p>关键点是 Max-Age：Spring 默认写的是会话 Cookie（没有 Max-Age），
     * 浏览器一关就删。这里统一补成持久 Cookie，登录才能跨浏览器重启保持。
     *
     * <p>SameSite/Secure 只在跨站 HTTPS 时才加，同源加 Secure 会让
     * http 本地调试收不到 Cookie。
     */
    private String buildSessionCookie(Cookie cookie, HttpServletRequest request) {
        StringBuilder header = new StringBuilder();
        header.append(cookie.getName()).append("=").append(cookie.getValue() == null ? "" : cookie.getValue());

        String path = cookie.getPath();
        header.append("; Path=").append(path == null || path.isBlank() ? "/" : path);

        if (cookie.getDomain() != null && !cookie.getDomain().isBlank()) {
            header.append("; Domain=").append(cookie.getDomain());
        }

        if (cookie.getMaxAge() > 0) {
            header.append("; Max-Age=").append(cookie.getMaxAge());
        } else if (cookie.getMaxAge() < 0) {
            // 服务端没给有效期（Spring 默认），按配置的时长补上
            header.append("; Max-Age=").append(maxAgeSeconds);
        }
        // Max-Age == 0 表示删除该 Cookie，此时不补 Max-Age，保持删除语义

        header.append("; HttpOnly");

        boolean crossSite = isCrossSiteRequest(request);
        if (isHttpsRequest(request)) {
            header.append("; Secure");
        }
        header.append("; SameSite=").append(crossSite ? "None" : "Lax");

        return header.toString();
    }

    private boolean isCrossSiteRequest(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) {
            return false;
        }
        try {
            URI originUri = URI.create(origin);
            String requestScheme = forwardedProto(request);
            int requestPort = request.getServerPort();
            int originPort = originUri.getPort() >= 0 ? originUri.getPort() : defaultPort(originUri.getScheme());
            int normalizedRequestPort = requestPort > 0 ? requestPort : defaultPort(requestScheme);
            return !originUri.getScheme().equalsIgnoreCase(requestScheme)
                    || !originUri.getHost().equalsIgnoreCase(request.getServerName())
                    || originPort != normalizedRequestPort;
        } catch (IllegalArgumentException exception) {
            return true;
        }
    }

    private boolean isHttpsRequest(HttpServletRequest request) {
        return "https".equalsIgnoreCase(forwardedProto(request)) || request.isSecure();
    }

    private String forwardedProto(HttpServletRequest request) {
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (forwardedProto == null || forwardedProto.isBlank()) {
            return request.getScheme();
        }
        int separator = forwardedProto.indexOf(',');
        return (separator >= 0 ? forwardedProto.substring(0, separator) : forwardedProto).trim();
    }

    private int defaultPort(String scheme) {
        if ("https".equalsIgnoreCase(scheme)) {
            return 443;
        }
        if ("http".equalsIgnoreCase(scheme)) {
            return 80;
        }
        return -1;
    }

    private boolean containsAttribute(String cookieHeader, String attribute) {
        return cookieHeader.toLowerCase().contains(attribute.toLowerCase());
    }
}
