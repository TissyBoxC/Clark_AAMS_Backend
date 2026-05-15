package io.github.tissyboxc.clark_aams_backend.importers.dto;

import java.util.Map;

public record LoginSessionDto(
        String cookie,
        String studentId,
        String successUrl,
        Map<String, Object> extra
) {
}
