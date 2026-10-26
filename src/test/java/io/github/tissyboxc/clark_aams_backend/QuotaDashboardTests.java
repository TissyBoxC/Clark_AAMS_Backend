package io.github.tissyboxc.clark_aams_backend;

import io.github.tissyboxc.clark_aams_backend.admin.QuotaDashboardDto;
import io.github.tissyboxc.clark_aams_backend.admin.QuotaDashboardService;
import io.github.tissyboxc.clark_aams_backend.security.QuotaGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配额看板（P5-4）。
 *
 * <p>识别、取件码、题目解析三项都消耗外部 AI 额度，
 * {@link QuotaGuard} 一直在按用户记录每日用量——
 * 但这些数字**从来没有对外暴露过**。
 *
 * <p>后果很具体：额度被刷光时，管理员只能看到一个用户报「今天用不了」，
 * 既不知道是谁、也不知道用掉了多少、更不知道是不是有人脚本刷。
 * 唯一能做的就是等第二天。
 *
 * <p>这个看板要回答三个问题：
 * <ol>
 *   <li>今天各业务域总共用了多少？</li>
 *   <li>谁用得最多？</li>
 *   <li>有没有人已经打到上限？</li>
 * </ol>
 *
 * <p>前提约束：{@link QuotaGuard} 的状态在内存里，
 * 所以看板必须**如实说明这个数字的边界**——
 * 重启归零。不能让人误以为是历史累计。
 */
class QuotaDashboardTests {

    private QuotaGuard quotaGuard;
    private QuotaDashboardService service;

    @BeforeEach
    void setUp() {
        quotaGuard = new QuotaGuard();
        service = new QuotaDashboardService(quotaGuard);
    }

    @Test
    @DisplayName("无任何调用时，看板不报错且总量为 0")
    void emptyDashboard() {
        QuotaDashboardDto dashboard = service.snapshot();

        assertNotNull(dashboard, "看板不能为空");
        assertEquals(0, dashboard.totalCalls(), "没人用过时总调用数应为 0");
        assertTrue(dashboard.users().isEmpty(), "不该凭空出现用户记录");
    }

    @Test
    @DisplayName("按业务域统计调用量")
    void countsByScope() {
        quotaGuard.acquireDaily("recognize", 1L, 100);
        quotaGuard.acquireDaily("recognize", 1L, 100);
        quotaGuard.acquireDaily("analyze", 1L, 100);
        quotaGuard.acquireDaily("pickup", 2L, 100);

        QuotaDashboardDto dashboard = service.snapshot();

        assertEquals(4, dashboard.totalCalls());
        assertEquals(2, dashboard.callsFor("recognize"), "识别调用应为 2 次");
        assertEquals(1, dashboard.callsFor("analyze"));
        assertEquals(1, dashboard.callsFor("pickup"));
    }

    @Test
    @DisplayName("按用户聚合，且能看出谁用得最多")
    void aggregatesByUser() {
        quotaGuard.acquireDaily("recognize", 7L, 100);
        quotaGuard.acquireDaily("recognize", 7L, 100);
        quotaGuard.acquireDaily("recognize", 7L, 100);
        quotaGuard.acquireDaily("recognize", 8L, 100);

        QuotaDashboardDto dashboard = service.snapshot();

        assertEquals(2, dashboard.users().size(), "应只出现两个用户");
        // 排序目的很实际：管理员要一眼看到「谁在刷」
        assertEquals(7L, dashboard.users().get(0).userId(), "用得最多的应排在最前");
        assertEquals(3, dashboard.users().get(0).totalCalls());
        assertEquals(8L, dashboard.users().get(1).userId());
    }

    @Test
    @DisplayName("标记出已达上限的用户")
    void marksExhaustedUsers() {
        // 上限 2，用满 2 次
        quotaGuard.acquireDaily("recognize", 11L, 2);
        quotaGuard.acquireDaily("recognize", 11L, 2);
        // 另一个用户只用 1 次
        quotaGuard.acquireDaily("recognize", 12L, 2);

        QuotaDashboardDto dashboard = service.snapshot();

        QuotaDashboardDto.UserUsage exhausted = dashboard.users().stream()
                .filter(u -> u.userId() == 11L)
                .findFirst()
                .orElseThrow();
        assertTrue(exhausted.exhaustedScopes().contains("recognize"),
                "用满配额的用户应被标记出来");
        assertTrue(exhausted.exhaustedScopes().contains("recognize"));

        QuotaDashboardDto.UserUsage normal = dashboard.users().stream()
                .filter(u -> u.userId() == 12L)
                .findFirst()
                .orElseThrow();
        assertTrue(normal.exhaustedScopes().isEmpty(), "没用满的不该被标记");
    }

    @Test
    @DisplayName("看板如实标注「重启归零」这一边界")
    void declaresInMemoryLimitation() {
        quotaGuard.acquireDaily("recognize", 1L, 100);

        QuotaDashboardDto dashboard = service.snapshot();

        // 不写清楚的话，管理员会把它当历史累计用，
        // 然后困惑「昨天明明有几百次，今天怎么是 0」
        assertTrue(dashboard.inMemoryOnly(),
                "内存态统计必须显式标注，否则会被误读为历史累计");
        assertNotNull(dashboard.note(), "应给出可读说明");
        assertTrue(dashboard.note().contains("重启"),
                "说明里要提到重启会归零，实际：" + dashboard.note());
    }
}
