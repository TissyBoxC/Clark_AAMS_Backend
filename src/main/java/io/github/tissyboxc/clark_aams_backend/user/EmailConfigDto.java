package io.github.tissyboxc.clark_aams_backend.user;

public record EmailConfigDto(
        boolean enabled,
        String provider,
        String endpoint,
        String senderAddress,
        String senderName,
        String verificationSubject,
        String htmlTemplate,
        boolean apiKeyConfigured
) {
}
