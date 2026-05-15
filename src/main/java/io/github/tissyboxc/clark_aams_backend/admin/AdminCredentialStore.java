package io.github.tissyboxc.clark_aams_backend.admin;

import io.github.tissyboxc.clark_aams_backend.ClarkAamsBackendApplication;
import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class AdminCredentialStore {
    private static final String DEFAULT_FILE_NAME = "admin-config.json";
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private static final int ITERATIONS = 120_000;

    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String configuredPath;
    private final String externalUsername;
    private final String externalPassword;
    private Path configPath;
    private StoredAdminCredential storedCredential;

    public AdminCredentialStore(
            ObjectMapper objectMapper,
            @Value("${clark-aams.admin.config-path:}") String configuredPath,
            @Value("${clark-aams.admin.username:}") String externalUsername,
            @Value("${clark-aams.admin.password:}") String externalPassword
    ) {
        this.objectMapper = objectMapper;
        this.configuredPath = configuredPath;
        this.externalUsername = normalize(externalUsername);
        this.externalPassword = normalize(externalPassword);
    }

    @PostConstruct
    public void initialize() {
        configPath = resolveConfigPath();
        if (Files.exists(configPath)) {
            storedCredential = readConfig(configPath);
        }
    }

    public synchronized boolean configured() {
        return externalConfigured() || storedConfigured();
    }

    public synchronized boolean registrationAvailable() {
        return !configured();
    }

    public synchronized boolean authenticate(String username, String password) {
        if (externalConfigured()) {
            return constantTimeEquals(externalUsername, normalize(username))
                    && constantTimeEquals(externalPassword, normalize(password));
        }
        if (!storedConfigured()) {
            return false;
        }
        return constantTimeEquals(storedCredential.username(), normalize(username))
                && verifyPassword(password, storedCredential.salt(), storedCredential.passwordHash());
    }

    public synchronized void register(String username, String password) {
        String normalizedUsername = normalize(username);
        String normalizedPassword = normalize(password);
        if (!registrationAvailable()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "管理员账号已配置");
        }
        if (normalizedUsername.isBlank() || normalizedPassword.length() < 6) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "账号不能为空，密码至少 6 位");
        }

        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        storedCredential = new StoredAdminCredential(
                normalizedUsername,
                Base64.getEncoder().encodeToString(salt),
                hashPassword(normalizedPassword, salt)
        );
        writeConfig(configPath, storedCredential);
    }

    public Path configPath() {
        return configPath;
    }

    private boolean externalConfigured() {
        return !externalUsername.isBlank() && !externalPassword.isBlank();
    }

    private boolean storedConfigured() {
        return storedCredential != null
                && !normalize(storedCredential.username()).isBlank()
                && !normalize(storedCredential.salt()).isBlank()
                && !normalize(storedCredential.passwordHash()).isBlank();
    }

    private StoredAdminCredential readConfig(Path path) {
        try {
            return objectMapper.readValue(path.toFile(), StoredAdminCredential.class);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Failed to read admin config: " + path, exception);
        }
    }

    private void writeConfig(Path path, StoredAdminCredential credential) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), credential);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to write admin config: " + path, exception);
        }
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

    private boolean verifyPassword(String password, String salt, String expectedHash) {
        byte[] saltBytes = Base64.getDecoder().decode(salt);
        String actualHash = hashPassword(normalize(password), saltBytes);
        return constantTimeEquals(expectedHash, actualHash);
    }

    private String hashPassword(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, HASH_BYTES * 8);
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to hash admin password", exception);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                normalize(left).getBytes(StandardCharsets.UTF_8),
                normalize(right).getBytes(StandardCharsets.UTF_8)
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private record StoredAdminCredential(
            String username,
            String salt,
            String passwordHash
    ) {
    }
}
