package io.github.tissyboxc.clark_aams_backend.ai;

public record AiConfigDto(
        String baseUrl,
        String model,
        boolean apiKeyConfigured
) {
}
