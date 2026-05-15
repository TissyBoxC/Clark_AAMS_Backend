package io.github.tissyboxc.clark_aams_backend.ai;

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

@Component
public class AiConfigStore {
    private static final String DEFAULT_FILE_NAME = "ai-config.json";

    private final ObjectMapper objectMapper;
    private final String configuredPath;
    private Path configPath;
    private AiStoredConfig config = new AiStoredConfig("", "", "");

    public AiConfigStore(
            ObjectMapper objectMapper,
            @Value("${clark-aams.ai.config-path:}") String configuredPath
    ) {
        this.objectMapper = objectMapper;
        this.configuredPath = configuredPath;
    }

    @PostConstruct
    public void initialize() {
        configPath = resolveConfigPath();
        if (Files.exists(configPath)) {
            config = normalize(readConfig(configPath));
            return;
        }
        writeConfig(configPath, config);
    }

    public synchronized AiConfigDto current() {
        return new AiConfigDto(
                normalize(config.baseUrl()),
                normalize(config.model()),
                !normalize(config.apiKey()).isBlank()
        );
    }

    public synchronized AiRuntimeConfig runtimeConfig() {
        return new AiRuntimeConfig(
                normalize(config.baseUrl()),
                normalize(config.apiKey()),
                normalize(config.model())
        );
    }

    public synchronized AiConfigDto save(AiConfigUpdateRequest request) {
        String apiKey = normalize(config.apiKey());
        if (Boolean.TRUE.equals(request.clearApiKey())) {
            apiKey = "";
        } else if (!normalize(request.apiKey()).isBlank()) {
            apiKey = normalize(request.apiKey());
        }

        config = new AiStoredConfig(
                normalize(request.baseUrl()),
                apiKey,
                normalize(request.model())
        );
        writeConfig(configPath, config);
        return current();
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

    private AiStoredConfig readConfig(Path path) {
        try {
            return objectMapper.readValue(path.toFile(), AiStoredConfig.class);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Failed to read AI config: " + path, exception);
        }
    }

    private void writeConfig(Path path, AiStoredConfig value) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to write AI config: " + path, exception);
        }
    }

    private AiStoredConfig normalize(AiStoredConfig value) {
        if (value == null) {
            return new AiStoredConfig("", "", "");
        }
        return new AiStoredConfig(
                normalize(value.baseUrl()),
                normalize(value.apiKey()),
                normalize(value.model())
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private record AiStoredConfig(
            String baseUrl,
            String apiKey,
            String model
    ) {
    }
}
