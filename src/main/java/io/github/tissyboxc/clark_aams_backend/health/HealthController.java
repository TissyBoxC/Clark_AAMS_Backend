package io.github.tissyboxc.clark_aams_backend.health;

import io.github.tissyboxc.clark_aams_backend.common.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping(value = "/api/v1", produces = "application/json;charset=UTF-8")
public class HealthController {
    private final String version;

    public HealthController(@Value("${app.version:1.0.0}") String version) {
        this.version = version;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of(
                "status", "UP",
                "version", version
        ));
    }
}
