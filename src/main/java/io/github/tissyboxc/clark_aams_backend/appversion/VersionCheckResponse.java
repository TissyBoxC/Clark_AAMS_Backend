package io.github.tissyboxc.clark_aams_backend.appversion;

import java.time.OffsetDateTime;
import java.util.List;

public record VersionCheckResponse(
        String platform,
        String channel,
        String currentVersion,
        Integer currentBuild,
        String latestVersion,
        Integer latestBuild,
        String minimumSupportedVersion,
        Integer minimumSupportedBuild,
        String updateType,
        boolean updateAvailable,
        boolean forceUpdate,
        String title,
        String message,
        List<String> releaseNotes,
        List<DownloadSourceDto> downloadSources,
        String releasePageUrl,
        OffsetDateTime checkedAt
) {
}
