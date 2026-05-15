package io.github.tissyboxc.clark_aams_backend.ai;

record AiRuntimeConfig(
        String baseUrl,
        String apiKey,
        String model
) {
    boolean ready() {
        return !blank(baseUrl) && !blank(apiKey) && !blank(model);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
