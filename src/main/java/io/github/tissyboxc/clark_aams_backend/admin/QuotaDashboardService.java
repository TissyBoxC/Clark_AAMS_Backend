package io.github.tissyboxc.clark_aams_backend.admin;

import io.github.tissyboxc.clark_aams_backend.security.QuotaGuard;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 聚合 {@link QuotaGuard} 的用量数据，产出运维看板（P5-4）。
 *
 * <p>看板要回答的三个问题都来自同一份原始数据：
 * <ol>
 *   <li>今天各业务域用了多少</li>
 *   <li>谁用得最多（排查脚本刷额度）</li>
 *   <li>谁已经打满（判断「用户说用不了」是不是配额问题）</li>
 * </ol>
 *
 * <h2>这个数字的边界，必须说清楚</h2>
 *
 * <p>统计来自 {@link QuotaGuard} 的**内存计数**，因此：
 * <ul>
 *   <li>进程重启即归零；</li>
 *   <li>多实例部署时每个实例只看到自己那份；</li>
 *   <li>超限时计数已经加过，所以数字是「嘗試次数」而非「成功次数」。</li>
 * </ul>
 *
 * <p>前两条会让管理员把看板当历史累计用而得出错误结论，
 * 所以在 {@link QuotaDashboardDto} 里用 {@code inMemoryOnly} 和 {@code note}
 * 显式标注，而不是留给对方去猜。
 */
@Service
public class QuotaDashboardService {

    private final QuotaGuard quotaGuard;

    public QuotaDashboardService(QuotaGuard quotaGuard) {
        this.quotaGuard = quotaGuard;
    }

    /** 生成当前快照。 */
    public QuotaDashboardDto snapshot() {
        List<QuotaGuard.UsageEntry> entries = quotaGuard.snapshotToday();

        Map<String, Integer> scopeTotals = new LinkedHashMap<>();
        Map<Long, Map<String, Integer>> perUser = new LinkedHashMap<>();
        // 上限用条目里记下的那个（当时真正生效的），不另读配置——
        // 两边一旦不一致，标记会失效且很难看出来
        Map<Long, List<String>> exhaustedByUser = new LinkedHashMap<>();

        int total = 0;
        for (QuotaGuard.UsageEntry entry : entries) {
            total += entry.count();
            scopeTotals.merge(entry.scope(), entry.count(), Integer::sum);
            perUser.computeIfAbsent(entry.userId(), ignored -> new LinkedHashMap<>())
                    .merge(entry.scope(), entry.count(), Integer::sum);
            if (isExhausted(entry.count(), entry.limit())) {
                exhaustedByUser
                        .computeIfAbsent(entry.userId(), ignored -> new ArrayList<>())
                        .add(entry.scope());
            }
        }

        List<QuotaDashboardDto.UserUsage> users = new ArrayList<>();
        perUser.forEach((userId, byScope) -> {
            int userTotal = byScope.values().stream().mapToInt(Integer::intValue).sum();

            List<QuotaDashboardDto.ScopeCount> scopeCounts = byScope.entrySet().stream()
                    .map(item -> new QuotaDashboardDto.ScopeCount(item.getKey(), item.getValue()))
                    // 同一用户内部也按用量降序，方便一眼看到主要消耗在哪
                    .sorted(Comparator.comparingInt(QuotaDashboardDto.ScopeCount::count).reversed())
                    .toList();

            List<String> exhausted = exhaustedByUser.getOrDefault(userId, List.of());

            users.add(new QuotaDashboardDto.UserUsage(userId, userTotal, scopeCounts, exhausted));
        });

        // 用量高的排前面：管理员打开看板先要看到「谁在刷」
        users.sort(Comparator
                .comparingInt(QuotaDashboardDto.UserUsage::totalCalls).reversed()
                .thenComparingLong(QuotaDashboardDto.UserUsage::userId));

        List<QuotaDashboardDto.ScopeUsage> scopes = scopeTotals.entrySet().stream()
                .map(item -> new QuotaDashboardDto.ScopeUsage(item.getKey(), item.getValue()))
                .sorted(Comparator.comparingInt(QuotaDashboardDto.ScopeUsage::calls).reversed())
                .toList();

        return new QuotaDashboardDto(
                total,
                users,
                scopes,
                true,
                "统计来自服务内存计数：进程重启后归零，多实例部署时各实例独立计数。"
                        + "数字为「嘗試次数」，包含被配额拒绝的那一次。"
        );
    }

    /**
     * 是否已达上限。
     *
     * <p>上限 &lt;= 0 表示不限，永远不会「用满」。
     */
    private boolean isExhausted(int used, int limit) {
        if (limit <= 0) {
            return false;
        }
        return used >= limit;
    }
}
