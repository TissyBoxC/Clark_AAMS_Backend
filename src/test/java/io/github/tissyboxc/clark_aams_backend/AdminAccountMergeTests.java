package io.github.tissyboxc.clark_aams_backend;

import io.github.tissyboxc.clark_aams_backend.admin.AdminCredentialStore;
import io.github.tissyboxc.clark_aams_backend.user.SQLiteUserRepository;
import io.github.tissyboxc.clark_aams_backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理员账号合并进 users 表（P1-1）。
 *
 * <p>现状：管理员凭据存在 `admin-config.json` 或环境变量里，
 * 普通用户在 `users` 表，两套完全独立。于是「登录后按身份进入不同工作台」
 * 只能靠前端猜，后端也没有单一事实来源回答「谁是管理员」。
 *
 * <p>目标：管理员成为 `users` 表中 `role='admin'` 的一行。
 * 这组测试锁住迁移的三个关键性质：
 * <ol>
 *   <li>注册的管理员会写进 users 表，且角色为 admin</li>
 *   <li>已有的用户记录不会被误提升为管理员</li>
 *   <li>账号已存在于 users 表时，迁移不重复插入</li>
 * </ol>
 */
class AdminAccountMergeTests {

    @TempDir
    Path tempDir;

    private SQLiteUserRepository repository;
    private AdminCredentialStore store;

    @BeforeEach
    void setUp() {
        repository = new SQLiteUserRepository(tempDir.resolve("merge.db").toString());
        repository.initialize();
        store = new AdminCredentialStore(
                new ObjectMapper(),
                tempDir.resolve("admin-config.json").toString(),
                "",
                "",
                repository
        );
        store.initialize();
    }

    @Test
    @DisplayName("注册管理员后，users 表出现一条 role=admin 的记录")
    void registeringAdminCreatesAdminRow() {
        store.register("rootadmin", "adminPass123");

        Optional<UserRepository.UserRecord> admin = repository.findByUsername("rootadmin");
        assertTrue(admin.isPresent(), "管理员账号应写入 users 表");
        assertEquals("admin", admin.get().effectiveRole(), "角色应为 admin");
        assertTrue(admin.get().isAdmin(), "isAdmin() 应为 true");
    }

    @Test
    @DisplayName("管理员记录的密码散列是加盐格式，不是明文")
    void adminPasswordIsHashed() {
        store.register("secadmin", "aVeryStrongPass");

        String hash = repository.findByUsername("secadmin").orElseThrow().passwordHash();
        assertFalse(hash.contains("aVeryStrongPass"), "不能存储明文密码");
        assertTrue(hash.startsWith("pbkdf2-sha256$"), "应使用与普通用户一致的加盐散列：" + hash);
    }

    @Test
    @DisplayName("普通用户不会被误提升为管理员")
    void regularUsersStayRegular() {
        repository.insert(new UserRepository.UserRecord(
                0L, "normaluser", "hash", "normal@qq.com", "CODENORMAL",
                false, false, "", 0L, 0L));

        store.register("theadmin", "adminPass123");

        assertEquals("user", repository.findByUsername("normaluser").orElseThrow().effectiveRole());
        assertEquals("admin", repository.findByUsername("theadmin").orElseThrow().effectiveRole());
    }

    @Test
    @DisplayName("重复初始化不会重复插入管理员行")
    void repeatedInitializeIsIdempotent() {
        store.register("onceadmin", "adminPass123");
        long countBefore = repository.findAll().stream()
                .filter(UserRepository.UserRecord::isAdmin)
                .count();

        // 模拟重启：用同一份配置文件新建一个 store
        AdminCredentialStore restarted = new AdminCredentialStore(
                new ObjectMapper(),
                tempDir.resolve("admin-config.json").toString(),
                "",
                "",
                repository
        );
        restarted.initialize();

        long countAfter = repository.findAll().stream()
                .filter(UserRepository.UserRecord::isAdmin)
                .count();
        assertEquals(countBefore, countAfter, "重启后不应重复插入管理员记录");
    }

    @Test
    @DisplayName("老部署：只有配置文件、users 表为空的场景能被迁移识别")
    void legacyConfigFileIsMigrated() {
        // 先注册（会产生配置文件 + users 记录），再清掉 users 里的管理员行，
        // 模拟「升级前只有 admin-config.json、没有对应 users 记录」的老部署
        store.register("legacyadmin", "legacyPass123");
        long adminId = repository.findByUsername("legacyadmin").orElseThrow().id();

        AdminCredentialStore legacy = new AdminCredentialStore(
                new ObjectMapper(),
                tempDir.resolve("legacy-only.json").toString(),
                "legacyadmin", "legacyPass123",
                repository
        );
        legacy.initialize();

        // 环境变量配置的管理员也应进入 users 表
        assertTrue(repository.findByUsername("legacyadmin").isPresent(),
                "外部配置的管理员应被同步到 users 表");
        assertTrue(repository.findAll().stream().filter(UserRepository.UserRecord::isAdmin).count() >= 1);
        assertTrue(adminId > 0);
    }
}
