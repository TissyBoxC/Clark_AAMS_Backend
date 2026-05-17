package io.github.tissyboxc.clark_aams_backend.user;

public record EmailConfigUpdateRequest(
        Boolean enabled,
        String provider,
        String endpoint,
        String senderAddress,
        String senderName,
        String verificationSubject,
        String htmlTemplate,
        String apiKey,
        Boolean clearApiKey
) {
}
