package io.github.tissyboxc.clark_aams_backend.ai;

public record AiConfigUpdateRequest(
        String baseUrl,
        String apiKey,
        String model,
        Boolean clearApiKey
) {
}
