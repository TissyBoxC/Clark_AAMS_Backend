package io.github.tissyboxc.clark_aams_backend.admin;

import io.github.tissyboxc.clark_aams_backend.ClarkAamsBackendApplication;
import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import io.github.tissyboxc.clark_aams_backend.user.PasswordHasher;
import io.github.tissyboxc.clark_aams_backend.user.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher = new PasswordHasher();

    @Autowired
    public AdminCredentialStore(
            ObjectMapper objectMapper,
            @Value("${clark-aams.admin.config-path:}") String configuredPath,
            @Value("${clark-aams.admin.username:}") String externalUsername,
            @Value("${clark-aams.admin.password:}") String externalPassword,
            UserRepository userRepository
    ) {
        this.objectMapper = objectMapper;
        this.configuredPath = configuredPath;
        this.externalUsername = normalize(externalUsername);
        this.externalPassword = normalize(externalPassword);
        this.userRepository = userRepository;
    }

    /** 便于测试构造（不注入仓储时退化为纯文件存储）。 */
    public AdminCredentialStore(
            ObjectMapper objectMapper,
            String configuredPath,
            String externalUsername,
            String externalPassword
    ) {
        this(objectMapper, configuredPath, externalUsername, externalPassword, null);
    }

    @PostConstruct
    public void initialize() {
        configPath = resolveConfigPath();
        if (Files.exists(configPath)) {
            storedCredential = readConfig(configPath);
        }
        syncAdminToUsersTable();
    }

    /**
     * 把管理员账号同步进 `users` 表（P1-1 账号表合并）。
     *
     * <p>合并的目标是让「谁是管理员」只有一处事实来源。但已有部署里
     * 管理员凭据可能来自两个地方：`admin-config.json`，或环境变量
     * `CLARK_AAMS_ADMIN_USERNAME/PASSWORD`。两种都要覆盖。
     *
     * <p>三条约束：
     * <ul>
     *   <li>幂等：users 表里已有同名管理员就不再插入（重启安全）</li>
     *   <li>不误提升：只处理管理员自己的账号名，绝不批量改别人的 role</li>
     *   <li>不覆盖已有密码：用户的密码以 users 表为准，这里只补缺</li>
     * </ul>
     */
    private void syncAdminToUsersTable() {
        if (userRepository == null) {
            return;
        }
        String username = "";
        String plainPassword = "";
        String hashedPassword = "";

        if (externalConfigured()) {
            username = externalUsername;
            plainPassword = externalPassword;
        } else if (storedConfigured()) {
            username = normalize(storedCredential.username());
            // 文件里存的是加盐散列，直接沿用，不要重新散列
            hashedPassword = storedCredential.salt().isEmpty()
                    ? normalize(storedCredential.passwordHash())
                    : storedCredential.salt() + "|" + storedCredential.passwordHash();
        }

        if (username.isBlank()) {
            return;
        }
        if (userRepository.findByUsername(username).isPresent()) {
            return;
        }

        String hash = !plainPassword.isBlank()
                ? passwordHasher.hash(plainPassword)
                : passwordHasher.hash(hashedPassword);
        userRepository.insert(new UserRepository.UserRecord(
                0L,
                username,
                hash,
                "",
                generateLoginCode(),
                false,
                false,
                "",
                0L,
                System.currentTimeMillis(),
                "admin"
        ));
    }

    private String generateLoginCode() {
        byte[] bytes = new byte[12];
        secureRandom.nextBytes(bytes);
        return "ADM" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
        if (userRepository != null && userRepository.findByUsername(normalizedUsername).isEmpty()) {
            userRepository.insert(new UserRepository.UserRecord(
                    0L,
                    normalizedUsername,
                    passwordHasher.hash(normalizedPassword),
                    "",
                    generateLoginCode(),
                    false,
                    false,
                    "",
                    0L,
                    System.currentTimeMillis(),
                    "admin"
            ));
        }
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

    /**
     * 解析管理员凭据文件位置。
     *
     * <p>与用户库、取件码保持一致地支持 {@code CLARK_AAMS_DATA_DIR}（P5-2 / P1-1）：
     * 管理员凭据也是可变数据，理应跟数据目录一起搬走。
     *
     * <p>原先它只看「工作目录」，导致设置数据目录后其余数据都搬了、
     * 管理员凭据还留在原地——容器里就成了「用户数据持久化了、
     * 管理员配置却在临时层」，重启即丢失且难以察觉。
     */
    private Path resolveConfigPath() {
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Path.of(configuredPath.trim()).toAbsolutePath().normalize();
        }
        String dataDir = System.getenv("CLARK_AAMS_DATA_DIR");
        if (dataDir != null && !dataDir.isBlank()) {
            return Path.of(dataDir.trim())
                    .resolve(DEFAULT_FILE_NAME)
                    .toAbsolutePath()
                    .normalize();
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
