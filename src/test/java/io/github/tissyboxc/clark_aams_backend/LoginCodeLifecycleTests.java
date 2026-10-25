package io.github.tissyboxc.clark_aams_backend;

import io.github.tissyboxc.clark_aams_backend.user.UserAccountService;
import io.github.tissyboxc.clark_aams_backend.user.UserProfileDto;
import io.github.tissyboxc.clark_aams_backend.user.UserRegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 登录码可吊销（审查条目 A2）与不回传（F4）。
 */
@SpringBootTest
@TestPropertySource(properties = {
        "clark-aams.client-version.config-path=${java.io.tmpdir}/clark-aams-logincode-test-client-version.json",
        "clark-aams.pickup.storage-dir=${java.io.tmpdir}/clark-aams-logincode-test-pickups",
        "clark-aams.import.unsupported-html-dir=${java.io.tmpdir}/clark-aams-logincode-test-unsupported-html",
        "clark-aams.user.db-path=${java.io.tmpdir}/clark-aams-logincode-test-users.db",
        "clark-aams.admin.username=test-admin",
        "clark-aams.admin.password=test-password"
})
class LoginCodeLifecycleTests {

    @Autowired
    private UserAccountService userAccountService;

    private UserProfileDto registerFreshUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return userAccountService.register(new UserRegisterRequest(
                "user_" + suffix,
                "pass_" + suffix,
                suffix + "@qq.com"
        ));
    }

    @Test
    @DisplayName("注册后可用登录码登录")
    void loginByCodeWorks() {
        UserProfileDto user = registerFreshUser();
        assertTrue(user.loginCode() != null && !user.loginCode().isBlank());

        UserProfileDto loggedIn = userAccountService.getByLoginCode(user.loginCode());
        assertEquals(user.id(), loggedIn.id());
    }

    @Test
    @DisplayName("轮换后旧登录码失效，新码可用（A2）")
    void rotateInvalidatesOldCode() {
        UserProfileDto user = registerFreshUser();
        String oldCode = user.loginCode();

        UserProfileDto rotated = userAccountService.rotateLoginCode(user.id());
        String newCode = rotated.loginCode();

        assertNotEquals(oldCode, newCode, "轮换必须产生新码");
        assertThrows(
                RuntimeException.class,
                () -> userAccountService.getByLoginCode(oldCode),
                "旧码必须立即失效"
        );
        assertEquals(user.id(), userAccountService.getByLoginCode(newCode).id());
    }

    @Test
    @DisplayName("连续轮换两次，只有最新码有效")
    void rotateTwiceOnlyLatestWorks() {
        UserProfileDto user = registerFreshUser();
        String code1 = user.loginCode();
        String code2 = userAccountService.rotateLoginCode(user.id()).loginCode();
        String code3 = userAccountService.rotateLoginCode(user.id()).loginCode();

        assertThrows(RuntimeException.class, () -> userAccountService.getByLoginCode(code1));
        assertThrows(RuntimeException.class, () -> userAccountService.getByLoginCode(code2));
        assertEquals(user.id(), userAccountService.getByLoginCode(code3).id());
    }

    @Test
    @DisplayName("空登录码被拒绝")
    void blankCodeRejected() {
        assertThrows(RuntimeException.class, () -> userAccountService.getByLoginCode(""));
        assertThrows(RuntimeException.class, () -> userAccountService.getByLoginCode("   "));
    }

    @Test
    @DisplayName("withoutLoginCode 抹掉凭据但保留其他字段（F4）")
    void withoutLoginCodeStripsCredential() {
        UserProfileDto user = registerFreshUser();
        UserProfileDto safe = user.withoutLoginCode();

        assertEquals("", safe.loginCode(), "对外资料不应包含登录码");
        assertEquals(user.id(), safe.id());
        assertEquals(user.username(), safe.username());
        assertEquals(user.email(), safe.email());
    }

    @Test
    @DisplayName("登录码长度符合预期，且字符集排除了易混淆字符")
    void loginCodeFormat() {
        UserProfileDto user = registerFreshUser();
        String code = user.loginCode();
        assertEquals(12, code.length());
        assertTrue(code.matches("[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]+"),
                "不应包含 0/O/I/1 等易混淆字符: " + code);
    }
}
