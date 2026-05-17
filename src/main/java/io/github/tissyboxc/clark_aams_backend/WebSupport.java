package io.github.tissyboxc.clark_aams_backend;

import io.github.tissyboxc.clark_aams_backend.common.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.util.Map;

@Controller
class PageController {
    @GetMapping({"/admin", "/admin/"})
    String admin() {
        return "redirect:/admin/index.html";
    }

    @GetMapping({"/api", "/api/", "/api.html"})
    String api() {
        return "redirect:/api/index.html";
    }

    @GetMapping({"/user", "/user/", "/user.html", "/my", "/my/", "/my.html"})
    String user() {
        return "redirect:/user/index.html";
    }

    @GetMapping({"/login", "/login/"})
    String login() {
        return "redirect:/auth/login.html";
    }

    @GetMapping({"/register", "/register/"})
    String register() {
        return "redirect:/auth/register.html";
    }

    @GetMapping({"/recognize", "/recognize/", "/recognize.html"})
    String recognize() {
        return "redirect:/user/index.html#recognize";
    }

    @GetMapping("/favicon.ico")
    String favicon() {
        return "forward:/site/favicon.ico";
    }
}

@RestController
@RequestMapping(value = "/api/v1", produces = "application/json;charset=UTF-8")
class HealthController {
    private final String version;

    HealthController(@Value("${app.version:1.0.0}") String version) {
        this.version = version;
    }

    @GetMapping("/health")
    ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of(
                "status", "UP",
                "version", version
        ));
    }
}

@Configuration
class CorsConfig {
    @Bean
    WebMvcConfigurer webMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOriginPatterns("*")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }
}

@Component
class RequestLogFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = System.currentTimeMillis() - start;
            log.info("{} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(), response.getStatus(), elapsedMs);
        }
    }
}
