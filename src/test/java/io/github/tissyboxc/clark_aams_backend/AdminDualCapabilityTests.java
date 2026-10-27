package io.github.tissyboxc.clark_aams_backend;

import io.github.tissyboxc.clark_aams_backend.user.SQLiteUserRepository;
import io.github.tissyboxc.clark_aams_backend.user.UserAccountService;
import io.github.tissyboxc.clark_aams_backend.user.UserProfileDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理员同时具备普通用户能力。
 *
 * <p>背景（用户实测反馈）：管理员登录管理台一切正常，但一旦去用课表、
 * 导入这些普通功能，就报「登录状态无效或已过期」。原因是
 * {@code establishAdminIdentity} 会把会话里的用户标记清掉，而管理员
 * 在 users 表里根本没有对应记录，于是所有用户接口都认为「没登录」。
 *
 * <p>现在按产品要求改成「同一账号两种能力」。这组测试锁住三个性质：
 * <ol>
 *   <li>管理员在 users 表里有 role='admin' 的记录</li>
 *   <li>{@link UserAccountService#ensureAdminUser} 是幂等的，重复调用不重复插入</li>
 *   <li>占位密码哈希无法被任何明文登录，避免绕过管理员凭据校验</li>
 * </ol>
 */
class AdminDualCapabilityTests {

    @TempDir
    Path tempDir;

    private SQLiteUserRepository repository;
    private UserAccountService service;

    @BeforeEach
    void setUp() {
        repository = new SQLiteUserRepository(tempDir.resolve("dual.db").toString());
        repository.initialize();
        service = new UserAccountService(repository);
    }

    @Test
    @DisplayName("管理员账号在 users 表里是 role=admin")
    void adminUserIsCreatedWithAdminRole() {
        UserProfileDto admin = service.ensureAdminUser("Provias");

        assertTrue(admin.isAdmin(), "管理员记录的角色必须是 admin，否则进不去管理台");
        assertEquals("Provias", admin.username());

        // 必须真的落到了表里，而不只是内存对象
        var stored = repository.findById(admin.id());
        assertTrue(stored.isPresent(), "管理员记录必须持久化到 users 表");
        assertTrue(stored.get().isAdmin());
    }

    @Test
    @DisplayName("重复调用 ensureAdminUser 不重复插入")
    void ensureAdminUserIsIdempotent() {
        UserProfileDto first = service.ensureAdminUser("Provias");
        UserProfileDto second = service.ensureAdminUser("Provias");

        assertEquals(first.id(), second.id(), "同一个管理员不能对应两条用户记录");
    }

    @Test
    @DisplayName("管理员记录带登录码，可以走用户侧接口")
    void adminRecordHasLoginCodeForUserFeatures() {
        UserProfileDto admin = service.ensureAdminUser("Provias");

        // 用户侧接口靠登录码/会话 id 定位用户；没有 id 就用不了任何用户功能
        assertNotEquals(0L, admin.id(), "管理员必须拿到真实的用户 id");
        assertTrue(
                admin.loginCode() != null && !admin.loginCode().isBlank(),
                "管理员也需要登录码，否则无法使用依赖它的用户功能"
        );
    }

    @Test
    @DisplayName("占位密码哈希不能被任意明文登录")
    void placeholderPasswordCannotBeUsedToLogIn() {
        service.ensureAdminUser("Provias");

        // 管理员密码由 AdminCredentialStore 单独校验。users 表里的占位哈希
        // 若碰巧能被某个明文匹配，就等于开了一条绕过管理员凭据的后门。
        assertThrows(
                Exception.class,
                () -> service.login(new io.github.tissyboxc.clark_aams_backend.user.UserLoginRequest(
                        "", "", "Provias", "")),
                "占位哈希不应该允许用空密码登录"
        );
    }

    @Test
    @DisplayName("同名普通用户被提升为管理员，且保留原记录")
    void existingRegularUserIsPromoted() {
        UserProfileDto regular = service.register(
                new io.github.tissyboxc.clark_aams_backend.user.UserRegisterRequest(
                        "Provias", "some-password", "provias@qq.com"));

        assertEquals("user", regular.role(), "注册出来的应该是普通用户");

        UserProfileDto admin = service.ensureAdminUser("Provias");

        assertEquals(regular.id(), admin.id(), "提权不应该新建第二条记录");
        assertTrue(admin.isAdmin(), "提权后角色必须是 admin");
    }
}
