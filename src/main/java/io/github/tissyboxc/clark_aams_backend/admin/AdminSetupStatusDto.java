package io.github.tissyboxc.clark_aams_backend.admin;

public record AdminSetupStatusDto(
        boolean configured,
        boolean registrationAvailable
) {
}
