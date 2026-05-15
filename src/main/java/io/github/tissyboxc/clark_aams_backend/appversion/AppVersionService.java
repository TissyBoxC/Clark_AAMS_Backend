package io.github.tissyboxc.clark_aams_backend.appversion;

import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class AppVersionService {
    private final AppVersionProperties properties;

    public AppVersionService(AppVersionProperties properties) {
        this.properties = properties;
    }

    public VersionCheckResponse check(VersionCheckRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        String currentVersion = normalize(request.currentVersion());
        Integer currentBuild = request.currentBuild();
        if (currentVersion.isBlank() && currentBuild == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "currentVersion 和 currentBuild 至少传一个");
        }

        UpdateType updateType = resolveUpdateType(currentVersion, currentBuild);
        List<DownloadSourceDto> downloadSources = properties.getDownloadSources().stream()
                .filter(source -> source.getUrl() != null && !source.getUrl().isBlank())
                .map(source -> new DownloadSourceDto(
                        normalize(source.getType()),
                        normalize(source.getLabel()),
                        normalize(source.getUrl()),
                        source.isPrimary(),
                        normalize(source.getDescription())
                ))
                .toList();

        String message = switch (updateType) {
            case REQUIRED -> properties.getRequiredUpdateMessage();
            case OPTIONAL -> properties.getOptionalUpdateMessage();
            case NONE -> "";
        };

        return new VersionCheckResponse(
                normalizeOrDefault(request.platform(), "android"),
                normalizeOrDefault(request.channel(), "stable"),
                currentVersion,
                currentBuild,
                properties.getLatestVersion(),
                properties.getLatestBuild(),
                properties.getMinimumSupportedVersion(),
                properties.getMinimumSupportedBuild(),
                updateType.value(),
                updateType != UpdateType.NONE,
                updateType == UpdateType.REQUIRED,
                updateType == UpdateType.NONE ? "" : properties.getTitle(),
                message,
                properties.getReleaseNotes(),
                downloadSources,
                normalize(properties.getReleasePageUrl()),
                OffsetDateTime.now()
        );
    }

    private UpdateType resolveUpdateType(String currentVersion, Integer currentBuild) {
        boolean requiredByBuild = currentBuild != null
                && properties.getMinimumSupportedBuild() > 0
                && currentBuild < properties.getMinimumSupportedBuild();
        boolean requiredByVersion = !currentVersion.isBlank()
                && SemanticVersion.parse(currentVersion)
                .compareTo(SemanticVersion.parse(properties.getMinimumSupportedVersion())) < 0;
        if (requiredByBuild || requiredByVersion) {
            return UpdateType.REQUIRED;
        }
        boolean optionalByBuild = currentBuild != null
                && properties.getLatestBuild() > 0
                && currentBuild < properties.getLatestBuild();
        boolean optionalByVersion = !currentVersion.isBlank()
                && SemanticVersion.parse(currentVersion)
                .compareTo(SemanticVersion.parse(properties.getLatestVersion())) < 0;
        if (optionalByBuild || optionalByVersion) {
            return UpdateType.OPTIONAL;
        }
        return UpdateType.NONE;
    }

    private String normalizeOrDefault(String value, String fallback) {
        String normalized = normalize(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
