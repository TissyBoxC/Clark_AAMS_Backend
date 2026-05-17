package io.github.tissyboxc.clark_aams_backend.user;

public record UserRegisterRequest(
        String username,
        String password,
        String qqEmail
) {
}
