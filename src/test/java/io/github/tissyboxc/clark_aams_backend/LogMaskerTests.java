package io.github.tissyboxc.clark_aams_backend;

import io.github.tissyboxc.clark_aams_backend.common.LogMasker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 日志脱敏（P5-4）。
 *
 * <p>核心断言只有一条：**完整敏感值不能出现在输出里**。
 * 因此除了逐字比较，还加了「原值不是结果的子串」这类检查，
 * 以防将来改实现时不小心把原值透传出去。
 */
class LogMaskerTests {

    @Test
    @DisplayName("邮箱保留首尾字符、隐藏中间，域名完整")
    void masksEmailLocalPart() {
        String masked = LogMasker.email("123456789@qq.com");

        assertEquals("1***9@qq.com", masked);
        assertFalse(masked.contains("2345678"), "中间部分必须被隐藏");
        assertTrue(masked.endsWith("@qq.com"), "域名保留便于定位邮件服务");
    }

    @Test
    @DisplayName("短邮箱不泄漏全部字符")
    void masksShortEmail() {
        assertEquals("a***@qq.com", LogMasker.email("ab@qq.com"));
        assertEquals("a***@qq.com", LogMasker.email("a@qq.com"));
    }

    @Test
    @DisplayName("邮箱原值永远不是结果的子串")
    void originalEmailNeverSurvives() {
        for (String email : new String[]{
                "123456789@qq.com",
                "abc@qq.com",
                "verylongmailboxname@foxmail.com",
                "x@y.com"
        }) {
            String masked = LogMasker.email(email);
            assertFalse(masked.contains(email), "原值泄漏：" + masked);
        }
    }

    @Test
    @DisplayName("空值与异常格式一律返回固定占位符")
    void invalidInputReturnsPlaceholder() {
        assertEquals("***", LogMasker.email(null));
        assertEquals("***", LogMasker.email(""));
        assertEquals("***", LogMasker.email("   "));
        assertEquals("***", LogMasker.email("no-at-sign"));
        assertEquals("***", LogMasker.email("@qq.com"));
        assertEquals("***", LogMasker.email("user@"));
    }

    @Test
    @DisplayName("登录码只保留前两位")
    void masksSecret() {
        String masked = LogMasker.secret("ABCD12EF34");

        assertEquals("AB***", masked);
        assertFalse(masked.contains("CD12EF34"), "凭据主体必须被隐藏");
    }

    @Test
    @DisplayName("短凭据不泄漏")
    void masksShortSecret() {
        assertEquals("**", LogMasker.secret("AB"));
        assertEquals("**", LogMasker.secret("A"));
        assertEquals("***", LogMasker.secret(null));
        assertEquals("***", LogMasker.secret("  "));
    }
}
