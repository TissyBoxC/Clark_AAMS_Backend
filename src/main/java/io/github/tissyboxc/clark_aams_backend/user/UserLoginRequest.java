package io.github.tissyboxc.clark_aams_backend.user;

public record UserLoginRequest(
        String loginCode,
        String qqEmail,
        String username,
        String password
) {
}
