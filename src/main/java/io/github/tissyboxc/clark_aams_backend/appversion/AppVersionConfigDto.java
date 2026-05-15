package io.github.tissyboxc.clark_aams_backend.appversion;

import java.util.List;

public record AppVersionConfigDto(
        String latestVersion,
        Integer latestBuild,
        String minimumSupportedVersion,
        Integer minimumSupportedBuild,
        String title,
        String optionalUpdateMessage,
        String requiredUpdateMessage,
        String releasePageUrl,
        List<String> releaseNotes,
        List<DownloadSourceDto> downloadSources
) {
}
