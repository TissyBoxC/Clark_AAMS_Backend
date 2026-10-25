package io.github.tissyboxc.clark_aams_backend;

import io.github.tissyboxc.clark_aams_backend.security.QuotaGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 配额与限流的单元测试（审查条目 D1/D2/D6）。 */
class QuotaGuardTest {

    private QuotaGuard quotaGuard;

    @BeforeEach
    void setUp() {
        quotaGuard = new QuotaGuard();
    }

    @Nested
    @DisplayName("每日配额")
    class Daily {

        @Test
        @DisplayName("未超限时放行")
        void allowsWithinLimit() {
            for (int i = 0; i < 3; i++) {
                assertDoesNotThrow(() -> quotaGuard.acquireDaily("recognize", 1L, 3));
            }
            assertEquals(3, quotaGuard.usedToday("recognize", 1L));
        }

        @Test
        @DisplayName("超过上限抛 42901")
        void rejectsOverLimit() {
            quotaGuard.acquireDaily("recognize", 1L, 2);
            quotaGuard.acquireDaily("recognize", 1L, 2);
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> quotaGuard.acquireDaily("recognize", 1L, 2)
            );
            assertEquals(ErrorCode.QUOTA_EXCEEDED, exception.errorCode());
            assertEquals(42901, exception.errorCode().code());
        }

        @Test
        @DisplayName("limit <= 0 表示不限")
        void unlimitedWhenNonPositive() {
            for (int i = 0; i < 100; i++) {
                assertDoesNotThrow(() -> quotaGuard.acquireDaily("recognize", 1L, 0));
            }
        }

        @Test
        @DisplayName("不同用户互不影响")
        void perUserIsolation() {
            quotaGuard.acquireDaily("recognize", 1L, 1);
            assertThrows(
                    BusinessException.class,
                    () -> quotaGuard.acquireDaily("recognize", 1L, 1)
            );
            assertDoesNotThrow(() -> quotaGuard.acquireDaily("recognize", 2L, 1));
        }

        @Test
        @DisplayName("不同业务域互不影响")
        void perScopeIsolation() {
            quotaGuard.acquireDaily("recognize", 1L, 1);
            assertDoesNotThrow(() -> quotaGuard.acquireDaily("pickup", 1L, 1));
        }
    }

    @Nested
    @DisplayName("滑动窗口限流")
    class Rate {

        @Test
        @DisplayName("窗口内超过次数抛 42902")
        void rejectsOverRate() {
            quotaGuard.acquireRate("recognize", 1L, 60_000L, 2);
            quotaGuard.acquireRate("recognize", 1L, 60_000L, 2);
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> quotaGuard.acquireRate("recognize", 1L, 60_000L, 2)
            );
            assertEquals(ErrorCode.RATE_LIMITED, exception.errorCode());
            assertEquals(42902, exception.errorCode().code());
        }

        @Test
        @DisplayName("窗口过期后重新计数")
        void resetsAfterWindow() throws InterruptedException {
            quotaGuard.acquireRate("recognize", 1L, 50L, 1);
            assertThrows(
                    BusinessException.class,
                    () -> quotaGuard.acquireRate("recognize", 1L, 50L, 1)
            );
            Thread.sleep(80L);
            assertDoesNotThrow(() -> quotaGuard.acquireRate("recognize", 1L, 50L, 1));
        }

        @Test
        @DisplayName("maxInWindow <= 0 表示不限")
        void unlimitedWhenNonPositive() {
            for (int i = 0; i < 50; i++) {
                assertDoesNotThrow(() -> quotaGuard.acquireRate("recognize", 1L, 60_000L, 0));
            }
        }
    }

    @Nested
    @DisplayName("运维")
    class Ops {

        @Test
        @DisplayName("reset 清空计数")
        void resetClears() {
            quotaGuard.acquireDaily("recognize", 1L, 1);
            assertEquals(1, quotaGuard.usedToday("recognize", 1L));
            quotaGuard.reset();
            assertEquals(0, quotaGuard.usedToday("recognize", 1L));
            assertDoesNotThrow(() -> quotaGuard.acquireDaily("recognize", 1L, 1));
        }

        @Test
        @DisplayName("未使用的用户 today 为 0")
        void unusedUserZero() {
            assertEquals(0, quotaGuard.usedToday("recognize", 999L));
            assertTrue(quotaGuard.usedToday("analyze", 1L) == 0);
        }
    }
}
