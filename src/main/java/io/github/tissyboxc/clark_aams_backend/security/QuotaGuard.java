package io.github.tissyboxc.clark_aams_backend.security;

import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 用户维度的调用限流与每日配额。
 *
 * <p>背景（审查条目 D1/D2/D6）：识别、取件码、题目解析都会消耗
 * 外部 AI 额度或磁盘，但没有任何限制，一个账号（甚至匿名调用）就能
 * 把额度刷光。
 *
 * <p>两层保护：
 * <ul>
 *   <li>{@link #acquireDaily(String, long)}：每日配额，跨天自动重置；</li>
 *   <li>{@link #acquireRate(String, long, long)}：滑动窗口限流，防脚本短时刷。</li>
 * </ul>
 *
 * <p>实现说明：状态放在内存，单实例部署足够；多实例部署时各实例独立
 * 计数，实际额度为「配置值 × 实例数」，后续迁移到 SQLite/Redis 即可
 * （对应 P5 阶段）。之所以先做内存版，是因为它不引新依赖、可立刻止血。
 */
@Component
public class QuotaGuard {

    private final Map<String, DailyCounter> dailyCounters = new ConcurrentHashMap<>();

    /**
     * 每个 key 最近一次使用的每日上限。
     *
     * <p>为什么要在计数里存上限：看板需要判断「这个用户是否已用满」，
     * 而那必须用**当时真正生效的那个上限**。
     * 若看板另读一份配置，两边一旦不一致（配置热改、测试传参不同），
     * 结论就是错的——而且错得很隐蔽：数字看起来正常，只是标记不出来。
     */
    private final Map<String, Integer> appliedLimits = new ConcurrentHashMap<>();
    private final Map<String, WindowCounter> windowCounters = new ConcurrentHashMap<>();

    /**
     * 占用一次每日配额。
     *
     * @param scope   业务域，如 {@code recognize}、{@code pickup}、{@code analyze}
     * @param userId  用户 id
     * @param limit   每日上限，&lt;=0 表示不限
     * @throws BusinessException 超限时抛 {@link ErrorCode#QUOTA_EXCEEDED}（42901）
     */
    public void acquireDaily(String scope, long userId, int limit) {
        if (limit <= 0) {
            return;
        }
        appliedLimits.put(key(scope, userId), limit);
        DailyCounter counter = dailyCounters.compute(
                key(scope, userId),
                (ignored, existing) -> {
                    LocalDate today = LocalDate.now();
                    if (existing == null || !existing.date().equals(today)) {
                        return new DailyCounter(today, new AtomicInteger(1));
                    }
                    existing.count().incrementAndGet();
                    return existing;
                }
        );
        if (counter.count().get() > limit) {
            throw new BusinessException(
                    ErrorCode.QUOTA_EXCEEDED,
                    "今日额度已用完（上限 " + limit + " 次），请明天再试"
            );
        }
    }

    /**
     * 滑动窗口限流。
     *
     * @param scope       业务域
     * @param userId      用户 id
     * @param windowMs    窗口长度（毫秒）
     * @param maxInWindow 窗口内最大次数，&lt;=0 表示不限
     * @throws BusinessException 超限时抛 {@link ErrorCode#RATE_LIMITED}（42902）
     */
    public void acquireRate(String scope, long userId, long windowMs, int maxInWindow) {
        if (maxInWindow <= 0 || windowMs <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        WindowCounter counter = windowCounters.compute(
                key(scope, userId),
                (ignored, existing) -> {
                    if (existing == null || now - existing.windowStart() >= windowMs) {
                        return new WindowCounter(now, new AtomicInteger(1));
                    }
                    existing.count().incrementAndGet();
                    return existing;
                }
        );
        if (counter.count().get() > maxInWindow) {
            long retryAfterMs = windowMs - (now - counter.windowStart());
            long retryAfterSeconds = Math.max(1, (retryAfterMs + 999) / 1000);
            throw new BusinessException(
                    ErrorCode.RATE_LIMITED,
                    "操作过于频繁，请 " + retryAfterSeconds + " 秒后再试"
            );
        }
    }

    /** 查询今日已用次数，用于返回剩余额度或后台看板。 */
    public int usedToday(String scope, long userId) {
        DailyCounter counter = dailyCounters.get(key(scope, userId));
        if (counter == null || !counter.date().equals(LocalDate.now())) {
            return 0;
        }
        return counter.count().get();
    }

    /**
     * 导出今日所有用量快照，供运维看板使用。
     *
     * <p>为什么需要单独一个方法：内部 key 是 {@code scope + ":" + userId}
     * 拼出来的字符串，外部拿到也无法还原成结构化的 (scope, userId)。
     * 与其让调用方去 split 字符串，不如在这里一次性还原好——
     * 拼 key 的规则只有这里知道，解析规则也该留在这里。
     *
     * <p>只返回**今天**的记录：跨天的旧计数器仍在 map 里
     * （等下次同 key 写入时被替换），但看板不该把它们算进去。
     *
     * <p>注意 {@link #acquireDaily} 的行为：**超限时计数已经加过了**。
     * 所以这里的数字是「嘗試次数」，包含被拒绝的那一次。
     * 看板必须如实这么标注。
     */
    public List<UsageEntry> snapshotToday() {
        LocalDate today = LocalDate.now();
        List<UsageEntry> entries = new ArrayList<>();
        dailyCounters.forEach((rawKey, counter) -> {
            if (!counter.date().equals(today)) {
                return;
            }
            int separator = rawKey.lastIndexOf(':');
            if (separator <= 0) {
                return;
            }
            String scope = rawKey.substring(0, separator);
            long userId;
            try {
                userId = Long.parseLong(rawKey.substring(separator + 1));
            } catch (NumberFormatException ignored) {
                return;
            }
            int limit = appliedLimits.getOrDefault(rawKey, 0);
            entries.add(new UsageEntry(scope, userId, counter.count().get(), limit));
        });
        return entries;
    }

    /**
     * 一条用量记录。
     *
     * @param scope 业务域
     * @param userId 用户 id
     * @param count 今日计数（含被拒绝的那次）
     * @param limit 当时生效的每日上限，&lt;=0 表示不限
     */
    public record UsageEntry(String scope, long userId, int count, int limit) {
    }

    /** 测试与运维用：清空所有计数。 */
    public void reset() {
        dailyCounters.clear();
        appliedLimits.clear();
        windowCounters.clear();
    }

    private String key(String scope, long userId) {
        return scope + ":" + userId;
    }

    private record DailyCounter(LocalDate date, AtomicInteger count) {
    }

    private record WindowCounter(long windowStart, AtomicInteger count) {
    }
}
