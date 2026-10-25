package io.github.tissyboxc.clark_aams_backend;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;

@Component
@Order(0)
class SessionCookieFilter extends OncePerRequestFilter {
    private static final String SET_COOKIE = "Set-Cookie";
    private static final String SESSION_COOKIE_NAME = "JSESSIONID=";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        HttpServletResponseWrapper responseWrapper = new HttpServletResponseWrapper(response) {
            @Override
            public void addCookie(Cookie cookie) {
                if (!shouldRewriteSessionCookie(cookie, request)) {
                    super.addCookie(cookie);
                    return;
                }
                super.addHeader(SET_COOKIE, buildCrossSiteSessionCookie(cookie));
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
    }

    private boolean shouldRewriteSessionCookie(Cookie cookie, HttpServletRequest request) {
        return cookie != null
                && "JSESSIONID".equals(cookie.getName())
                && isCrossSiteRequest(request)
                && isHttpsRequest(request);
    }

    private String rewriteSetCookie(String headerName, String headerValue, HttpServletRequest request) {
        if (!SET_COOKIE.equalsIgnoreCase(headerName) || headerValue == null || !headerValue.startsWith(SESSION_COOKIE_NAME)) {
            return headerValue;
        }
        if (!isCrossSiteRequest(request) || !isHttpsRequest(request)) {
            return headerValue;
        }

        String rewritten = headerValue;
        if (!containsAttribute(rewritten, "SameSite=")) {
            rewritten += "; SameSite=None";
        }
        if (!containsAttribute(rewritten, "Secure")) {
            rewritten += "; Secure";
        }
        return rewritten;
    }

    private String buildCrossSiteSessionCookie(Cookie cookie) {
        StringBuilder header = new StringBuilder();
        header.append(cookie.getName()).append("=").append(cookie.getValue() == null ? "" : cookie.getValue());

        if (cookie.getPath() != null && !cookie.getPath().isBlank()) {
            header.append("; Path=").append(cookie.getPath());
        }
        if (cookie.getDomain() != null && !cookie.getDomain().isBlank()) {
            header.append("; Domain=").append(cookie.getDomain());
        }
        if (cookie.getMaxAge() >= 0) {
            header.append("; Max-Age=").append(cookie.getMaxAge());
        }
        header.append("; Secure");
        if (cookie.isHttpOnly()) {
            header.append("; HttpOnly");
        }
        header.append("; SameSite=None");
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
