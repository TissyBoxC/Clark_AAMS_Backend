package io.github.tissyboxc.clark_aams_backend.security;

import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
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

    /** 测试与运维用：清空所有计数。 */
    public void reset() {
        dailyCounters.clear();
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
