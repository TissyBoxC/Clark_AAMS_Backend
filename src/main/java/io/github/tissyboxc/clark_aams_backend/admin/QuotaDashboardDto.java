package io.github.tissyboxc.clark_aams_backend.admin;

import java.util.List;

/**
 * 配额看板快照（P5-4）。
 *
 * @param totalCalls   今日全部业务域的调用次数合计
 * @param users        按用量降序排列的用户明细
 * @param scopes       按用量降序排列的业务域汇总
 * @param inMemoryOnly 统计是否只来自内存（重启即归零）
 * @param note         给人看的边界说明
 */
public record QuotaDashboardDto(
        int totalCalls,
        List<UserUsage> users,
        List<ScopeUsage> scopes,
        boolean inMemoryOnly,
        String note
) {

    /**
     * 单个用户的用量。
     *
     * @param userId         用户 id
     * @param totalCalls     该用户今日总调用次数
     * @param byScope        各业务域的调用次数
     * @param exhaustedScopes 已达上限的业务域
     */
    public record UserUsage(
            long userId,
            int totalCalls,
            List<ScopeCount> byScope,
            List<String> exhaustedScopes
    ) {
    }

    /**
     * 单个业务域的用量计数。
     *
     * @param scope 业务域名，如 {@code recognize}
     * @param count 次数
     */
    public record ScopeCount(String scope, int count) {
    }

    /**
     * 业务域汇总。
     *
     * @param scope 业务域名
     * @param calls 次数
     */
    public record ScopeUsage(String scope, int calls) {
    }

    /** 取某个业务域今日的总调用量，没有记录时返回 0。 */
    public int callsFor(String scope) {
        return scopes.stream()
                .filter(item -> item.scope().equals(scope))
                .mapToInt(ScopeUsage::calls)
                .findFirst()
                .orElse(0);
    }
}
