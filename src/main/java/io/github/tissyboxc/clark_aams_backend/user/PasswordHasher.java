package io.github.tissyboxc.clark_aams_backend.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 用户密码散列（P1-1 前置安全修复）。
 *
 * <p><b>为什么需要这个类</b>：原实现是
 * {@code MessageDigest.getInstance("SHA-256").digest(password)} —— 单次、无盐。
 * 这有两个致命问题：
 * <ol>
 *   <li><b>无盐</b>：相同密码得到相同散列。泄漏后一眼能看出哪些用户用了同一个密码，
 *       也可以拿彩虹表批量反查</li>
 *   <li><b>单次快哈希</b>：SHA-256 是设计来「算得快」的，GPU 每秒能试上亿次，
 *       普通强度的密码在可接受时间内就能穷举出来</li>
 * </ol>
 * 而同一个系统里管理员账号用的是 PBKDF2（12 万次迭代 + 16 字节盐）——
 * 权限更低的人保护反而更弱，这是明显的失衡。
 *
 * <p><b>存储格式</b>：{@code pbkdf2-sha256$<iterations>$<base64-salt>$<base64-hash>}。
 * 带算法与参数前缀，将来要换算法或提高迭代次数时，
 * 可以按前缀识别老格式，无需额外的「格式版本」字段。
 *
 * <p><b>兼容历史数据</b>：识别出不含 {@code $} 分隔符的散列视为旧格式
 * （裸 SHA-256 hex），仍允许其通过校验，并在登录成功后**机会式升级**为新格式。
 * 这样不用停机做一次性迁移，用户下次登录时自然完成升级。
 */
public class PasswordHasher {
    private static final Logger log = LoggerFactory.getLogger(PasswordHasher.class);

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "pbkdf2-sha256";
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final int ITERATIONS = 210_000;

    private final SecureRandom secureRandom = new SecureRandom();

    /** 生成新格式散列。 */
    public String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        byte[] derived = derive(password, salt, ITERATIONS);
        return PREFIX
                + "$" + ITERATIONS
                + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(derived);
    }

    /**
     * 校验密码。兼容旧格式（裸 SHA-256）。
     *
     * @return 匹配返回 true；散列格式无法解析时返回 false（不抛异常）
     */
    public boolean matches(String password, String storedHash) {
        if (password == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        String trimmed = storedHash.trim();
        if (!trimmed.startsWith(PREFIX + "$")) {
            return matchesLegacy(password, trimmed);
        }
        String[] parts = trimmed.split("\\$");
        if (parts.length != 4) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(password, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException exception) {
            log.warn("Malformed stored password hash; rejecting login attempt");
            return false;
        }
    }

    /** 该散列是否是待升级的旧格式。 */
    public boolean needsUpgrade(String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            return false;
        }
        String trimmed = storedHash.trim();
        if (!trimmed.startsWith(PREFIX + "$")) {
            return true;
        }
        String[] parts = trimmed.split("\\$");
        if (parts.length != 4) {
            return true;
        }
        try {
            return Integer.parseInt(parts[1]) < ITERATIONS;
        } catch (NumberFormatException exception) {
            return true;
        }
    }

    private boolean matchesLegacy(String password, String storedHash) {
        String legacy = sha256Hex(password);
        return MessageDigest.isEqual(
                legacy.getBytes(StandardCharsets.UTF_8),
                storedHash.toLowerCase().getBytes(StandardCharsets.UTF_8)
        );
    }

    private String sha256Hex(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(password.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private byte[] derive(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(
                    password.toCharArray(), salt, iterations, HASH_BITS);
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to derive password hash", exception);
        }
    }
}
