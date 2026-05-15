package io.github.tissyboxc.clark_aams_backend.appversion;

import io.github.tissyboxc.clark_aams_backend.ClarkAamsBackendApplication;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class AppVersionConfigStore {
    private static final String DEFAULT_FILE_NAME = "client-version.json";

    private final AppVersionProperties properties;
    private final ObjectMapper objectMapper;
    private final String configuredPath;
    private Path configPath;

    public AppVersionConfigStore(
            AppVersionProperties properties,
            ObjectMapper objectMapper,
            @Value("${clark-aams.client-version.config-path:}") String configuredPath
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.configuredPath = configuredPath;
    }

    @PostConstruct
    public void initialize() {
        configPath = resolveConfigPath();
        if (Files.exists(configPath)) {
            apply(readConfig(configPath));
            return;
        }
        writeConfig(configPath, current());
    }

    public synchronized AppVersionConfigDto current() {
        return snapshot();
    }

    public synchronized AppVersionConfigDto save(AppVersionUpdateRequest request) {
        AppVersionConfigDto config = normalize(request);
        apply(config);
        AppVersionConfigDto saved = snapshot();
        writeConfig(configPath, saved);
        return saved;
    }

    public Path configPath() {
        return configPath;
    }

    private Path resolveConfigPath() {
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Path.of(configuredPath.trim()).toAbsolutePath().normalize();
        }
        Path codeSource = codeSourcePath();
        if (codeSource != null && Files.isRegularFile(codeSource)) {
            return codeSource.getParent().resolve(DEFAULT_FILE_NAME).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.dir")).resolve(DEFAULT_FILE_NAME).toAbsolutePath().normalize();
    }

    private Path codeSourcePath() {
        try {
            return Path.of(ClarkAamsBackendApplication.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
        } catch (URISyntaxException | NullPointerException exception) {
            return null;
        }
    }

    private AppVersionConfigDto readConfig(Path path) {
        try {
            return objectMapper.readValue(path.toFile(), AppVersionConfigDto.class);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Failed to read client version config: " + path, exception);
        }
    }

    private void writeConfig(Path path, AppVersionConfigDto config) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), config);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to write client version config: " + path, exception);
        }
    }

    private AppVersionConfigDto snapshot() {
        return new AppVersionConfigDto(
                normalize(properties.getLatestVersion()),
                properties.getLatestBuild(),
                normalize(properties.getMinimumSupportedVersion()),
                properties.getMinimumSupportedBuild(),
                normalize(properties.getTitle()),
                normalize(properties.getOptionalUpdateMessage()),
                normalize(properties.getRequiredUpdateMessage()),
                normalize(properties.getReleasePageUrl()),
                normalizeNotes(properties.getReleaseNotes()),
                properties.getDownloadSources().stream()
                        .filter(source -> source != null)
                        .map(source -> new DownloadSourceDto(
                                normalize(source.getType()),
                                normalize(source.getLabel()),
                                normalize(source.getUrl()),
                                source.isPrimary(),
                                normalize(source.getDescription())
                        ))
                        .toList()
        );
    }

    private AppVersionConfigDto normalize(AppVersionUpdateRequest request) {
        return new AppVersionConfigDto(
                normalize(request.latestVersion()),
                request.latestBuild(),
                normalize(request.minimumSupportedVersion()),
                request.minimumSupportedBuild(),
                normalize(request.title()),
                normalize(request.optionalUpdateMessage()),
                normalize(request.requiredUpdateMessage()),
                normalize(request.releasePageUrl()),
                normalizeNotes(request.releaseNotes()),
                normalizeDownloadSources(request.downloadSources())
        );
    }

    private void apply(AppVersionConfigDto config) {
        properties.setLatestVersion(normalize(config.latestVersion()));
        properties.setLatestBuild(config.latestBuild() == null ? 0 : config.latestBuild());
        properties.setMinimumSupportedVersion(normalize(config.minimumSupportedVersion()));
        properties.setMinimumSupportedBuild(config.minimumSupportedBuild() == null ? 0 : config.minimumSupportedBuild());
        properties.setTitle(normalize(config.title()));
        properties.setOptionalUpdateMessage(normalize(config.optionalUpdateMessage()));
        properties.setRequiredUpdateMessage(normalize(config.requiredUpdateMessage()));
        properties.setReleasePageUrl(normalize(config.releasePageUrl()));
        properties.setReleaseNotes(normalizeNotes(config.releaseNotes()));
        properties.setDownloadSources(toPropertyDownloadSources(config.downloadSources()));
    }

    private List<String> normalizeNotes(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(this::normalize)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private List<DownloadSourceDto> normalizeDownloadSources(List<DownloadSourceDto> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(source -> source != null && !normalize(source.url()).isBlank())
                .map(source -> new DownloadSourceDto(
                        normalize(source.type()),
                        normalize(source.label()),
                        normalize(source.url()),
                        source.primary(),
                        normalize(source.description())
                ))
                .toList();
    }

    private List<AppVersionProperties.DownloadSource> toPropertyDownloadSources(List<DownloadSourceDto> values) {
        List<AppVersionProperties.DownloadSource> sources = new ArrayList<>();
        for (DownloadSourceDto value : normalizeDownloadSources(values)) {
            AppVersionProperties.DownloadSource source = new AppVersionProperties.DownloadSource();
            source.setType(value.type());
            source.setLabel(value.label());
            source.setUrl(value.url());
            source.setPrimary(value.primary());
            source.setDescription(value.description());
            sources.add(source);
        }
        return sources;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
