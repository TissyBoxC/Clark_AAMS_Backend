package io.github.tissyboxc.clark_aams_backend.user;

import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EmailCodeCooldownService {
    private static final Duration COOLDOWN = Duration.ofSeconds(300);

    private final Map<Long, Instant> lastSentAtByUserId = new ConcurrentHashMap<>();

    public CooldownTicket reserve(long userId) {
        Instant now = Instant.now();
        Instant reservedAt = lastSentAtByUserId.compute(userId, (key, existing) -> {
            if (existing == null || cooldownExpired(existing, now)) {
                return now;
            }
            throw cooldownException(existing, now);
        });
        return new CooldownTicket(userId, reservedAt);
    }

    public void release(CooldownTicket ticket) {
        if (ticket == null) {
            return;
        }
        lastSentAtByUserId.computeIfPresent(ticket.userId(), (key, existing) ->
                existing.equals(ticket.reservedAt()) ? null : existing
        );
    }

    private boolean cooldownExpired(Instant lastSentAt, Instant now) {
        return Duration.between(lastSentAt, now).compareTo(COOLDOWN) >= 0;
    }

    private BusinessException cooldownException(Instant lastSentAt, Instant now) {
        long remainingSeconds = COOLDOWN.minus(Duration.between(lastSentAt, now)).toSeconds();
        if (remainingSeconds < 1) {
            remainingSeconds = 1;
        }
        return new BusinessException(
                ErrorCode.BAD_REQUEST,
                "验证码发送过于频繁，请 " + remainingSeconds + " 秒后再试"
        );
    }

    public record CooldownTicket(long userId, Instant reservedAt) {
    }
}
