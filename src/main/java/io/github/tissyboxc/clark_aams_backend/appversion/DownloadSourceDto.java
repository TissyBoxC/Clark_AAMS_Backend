package io.github.tissyboxc.clark_aams_backend.appversion;

public record DownloadSourceDto(
        String type,
        String label,
        String url,
        boolean primary,
        String description
) {
}
