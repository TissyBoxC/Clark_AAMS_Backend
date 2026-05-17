package io.github.tissyboxc.clark_aams_backend.user;

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
public class EmailConfigStore {
    private static final String DEFAULT_FILE_NAME = "email-config.json";

    private final ObjectMapper objectMapper;
    private final String configuredPath;
    private Path configPath;
    private StoredEmailConfig config = new StoredEmailConfig(false, "", "", "", "", "Clark AAMS QQ 邮箱验证码", defaultHtmlTemplate(), "");

    public EmailConfigStore(
            ObjectMapper objectMapper,
            @Value("${clark-aams.email.config-path:}") String configuredPath
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

    public synchronized EmailConfigDto current() {
        return new EmailConfigDto(
                config.enabled(),
                normalize(config.provider()),
                normalize(config.endpoint()),
                normalize(config.senderAddress()),
                normalize(config.senderName()),
                normalize(config.verificationSubject()),
                normalizeTemplate(config.htmlTemplate()),
                !normalize(config.apiKey()).isBlank()
        );
    }

    public synchronized StoredEmailConfig runtimeConfig() {
        return config;
    }

    public synchronized EmailConfigDto save(EmailConfigUpdateRequest request) {
        String apiKey = normalize(config.apiKey());
        if (request != null && Boolean.TRUE.equals(request.clearApiKey())) {
            apiKey = "";
        } else if (request != null && !normalize(request.apiKey()).isBlank()) {
            apiKey = normalize(request.apiKey());
        }
        config = new StoredEmailConfig(
                request != null && Boolean.TRUE.equals(request.enabled()),
                normalize(request == null ? null : request.provider()),
                normalize(request == null ? null : request.endpoint()),
                normalize(request == null ? null : request.senderAddress()),
                normalize(request == null ? null : request.senderName()),
                normalize(request == null ? null : request.verificationSubject()),
                normalizeTemplate(request == null ? null : request.htmlTemplate()),
                apiKey
        );
        writeConfig(configPath, config);
        return current();
    }

    public Path configPath() {
        return configPath;
    }

    private StoredEmailConfig readConfig(Path path) {
        try {
            return objectMapper.readValue(path.toFile(), StoredEmailConfig.class);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Failed to read email config: " + path, exception);
        }
    }

    private void writeConfig(Path path, StoredEmailConfig value) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to write email config: " + path, exception);
        }
    }

    private StoredEmailConfig normalize(StoredEmailConfig value) {
        if (value == null) {
            return config;
        }
        return new StoredEmailConfig(
                value.enabled(),
                normalize(value.provider()),
                normalize(value.endpoint()),
                normalize(value.senderAddress()),
                normalize(value.senderName()),
                normalize(value.verificationSubject()),
                normalizeTemplate(value.htmlTemplate()),
                normalize(value.apiKey())
        );
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

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeTemplate(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? defaultHtmlTemplate() : normalized;
    }

    private static String defaultHtmlTemplate() {
        return """
                <div style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;line-height:1.7;color:#3f2431">
                  <h2 style="margin:0 0 12px">Clark AAMS QQ 邮箱认定</h2>
                  <p>您正在进行 QQ 邮箱认定，请在页面中输入以下验证码：</p>
                  <div style="display:inline-block;padding:12px 18px;border-radius:8px;background:#fff0f7;color:#c93b78;font-size:28px;font-weight:800;letter-spacing:4px">{{code}}</div>
                  <p style="color:#8d6274">如果这不是你的操作，请忽略本邮件。</p>
                </div>
                """;
    }

    public record StoredEmailConfig(
            boolean enabled,
            String provider,
            String endpoint,
            String senderAddress,
            String senderName,
            String verificationSubject,
            String htmlTemplate,
            String apiKey
    ) {
    }
}
