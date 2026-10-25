package io.github.tissyboxc.clark_aams_backend;

import io.github.tissyboxc.clark_aams_backend.user.UserAccountService;
import io.github.tissyboxc.clark_aams_backend.user.UserRegisterRequest;
import io.github.tissyboxc.clark_aams_backend.user.SQLiteUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户密码散列强度（P1-1 的前置安全修复）。
 *
 * <p>原实现用**单次无盐 SHA-256**：
 * <pre>
 * MessageDigest.getInstance("SHA-256").digest(password.getBytes())
 * </pre>
 * 这有两个致命问题：
 * <ol>
 *   <li><b>无盐</b>：相同密码得到相同散列，可预计算彩虹表批量反查；
 *       泄漏后一眼能看出哪些用户用了同一个密码</li>
 *   <li><b>单次</b>：SHA-256 是快哈希，GPU 每秒能试上亿次，
 *       8 位密码的穷举在可接受时间内就能完成</li>
 * </ol>
 * 与此同时管理员账号却用了 PBKDF2（12 万次迭代 + 16 字节盐）——
 * 同一系统里「权限更低的人」密码保护反而更弱。
 */
class UserPasswordHashingTests {

    @TempDir
    Path tempDir;

    private SQLiteUserRepository repository;
    private UserAccountService service;

    @BeforeEach
    void setUp() {
        repository = new SQLiteUserRepository(tempDir.resolve("hash.db").toString());
        repository.initialize();
        service = new UserAccountService(repository);
    }

    private UserRegisterRequest request(String username, String password, String email) {
        return new UserRegisterRequest(username, password, email);
    }

    private String storedHash(String username) {
        return repository.findAll().stream()
                .filter(user -> user.username().equals(username))
                .findFirst()
                .orElseThrow()
                .passwordHash();
    }

    @Test
    @DisplayName("相同密码必须产生不同散列（证明加了盐）")
    void samePasswordProducesDifferentHashes() {
        service.register(request("alice", "samePassword123", "alice@qq.com"));
        service.register(request("bob", "samePassword123", "bob@qq.com"));

        String aliceHash = storedHash("alice");
        String bobHash = storedHash("bob");

        assertNotEquals(
                aliceHash,
                bobHash,
                "两个用户用同一密码却得到相同散列，说明没有盐："
                        + "泄漏后可一眼看出谁用了相同密码，也能用彩虹表批量反查"
        );
    }

    @Test
    @DisplayName("散列不能是密码的裸 SHA-256")
    void hashIsNotPlainSha256() throws Exception {
        service.register(request("carol", "mySecret999", "carol@qq.com"));

        String stored = storedHash("carol");
        String plainSha256 = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                        .digest("mySecret999".getBytes(java.nio.charset.StandardCharsets.UTF_8))
        );

        assertNotEquals(plainSha256, stored, "存储的散列等同于裸 SHA-256，没有加盐/迭代");
        assertFalse(stored.equalsIgnoreCase(plainSha256), "大小写不同也算泄漏");
    }

    @Test
    @DisplayName("散列不应是 64 位十六进制（单次 SHA-256 的特征）")
    void hashIsNotSingleSha256Shape() {
        service.register(request("dave", "anotherPass1", "dave@qq.com"));

        String stored = storedHash("dave");
        boolean looksLikeRawSha256 = stored.matches("[0-9a-fA-F]{64}");

        assertFalse(
                looksLikeRawSha256,
                "散列形态与单次 SHA-256 完全一致（64 位 hex），疑似仍是快哈希：" + stored
        );
    }

    @Test
    @DisplayName("正确密码能登录，错误密码不能")
    void loginStillWorksAfterHashingChange() {
        service.register(request("erin", "correctHorse", "erin@qq.com"));

        var profile = service.login(
                new io.github.tissyboxc.clark_aams_backend.user.UserLoginRequest(
                        "", "", "erin", "correctHorse"));
        assertEquals("erin", profile.username());

        org.junit.jupiter.api.Assertions.assertThrows(
                io.github.tissyboxc.clark_aams_backend.common.BusinessException.class,
                () -> service.login(new io.github.tissyboxc.clark_aams_backend.user.UserLoginRequest(
                        "", "", "erin", "wrongPassword"))
        );
    }

    @Test
    @DisplayName("旧格式（裸 SHA-256）的历史账号仍能登录，说明迁移是兼容的")
    void legacySha256AccountsStillLogIn() throws Exception {
        // 模拟迁移前写入的历史数据
        String legacyHash = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                        .digest("legacyPass1".getBytes(java.nio.charset.StandardCharsets.UTF_8))
        );
        repository.insert(new io.github.tissyboxc.clark_aams_backend.user.UserRepository.UserRecord(
                0L, "legacy", legacyHash, "legacy@qq.com", "CODELEGACY",
                false, false, "", 0L, 0L
        ));

        var profile = service.login(
                new io.github.tissyboxc.clark_aams_backend.user.UserLoginRequest(
                        "", "", "legacy", "legacyPass1"));

        assertEquals("legacy", profile.username(), "历史账号不能因为换了散列算法就登不进来");
    }

    @Test
    @DisplayName("旧账号登录成功后，散列应被升级为新格式")
    void legacyHashIsUpgradedOnLogin() throws Exception {
        String legacyHash = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                        .digest("upgradeMe1".getBytes(java.nio.charset.StandardCharsets.UTF_8))
        );
        repository.insert(new io.github.tissyboxc.clark_aams_backend.user.UserRepository.UserRecord(
                0L, "upmate", legacyHash, "upmate@qq.com", "CODEUP",
                false, false, "", 0L, 0L
        ));

        service.login(new io.github.tissyboxc.clark_aams_backend.user.UserLoginRequest(
                "", "", "upmate", "upgradeMe1"));

        String after = storedHash("upmate");
        assertNotEquals(legacyHash, after, "登录成功后应把旧散列升级为新格式（机会式迁移）");
        assertTrue(after.contains(":") || after.startsWith("pbkdf2"),
                "新散列应带算法标识，便于将来再升级：" + after);
    }
}
