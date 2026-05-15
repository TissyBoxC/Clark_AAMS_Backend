package io.github.tissyboxc.clark_aams_backend.appversion;

import java.util.Map;

public record VersionCheckRequest(
        String platform,
        String currentVersion,
        Integer currentBuild,
        String channel,
        Map<String, Object> extra
) {
}
