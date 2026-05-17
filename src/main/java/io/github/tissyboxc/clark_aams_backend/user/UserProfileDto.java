package io.github.tissyboxc.clark_aams_backend.user;

public record UserProfileDto(
        long id,
        String username,
        String email,
        String loginCode,
        boolean emailBound,
        boolean emailVerified,
        long questionAnalysisCount
) {
}
