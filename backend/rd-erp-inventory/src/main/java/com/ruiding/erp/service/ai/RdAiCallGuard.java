package com.ruiding.erp.service.ai;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RdAiCallGuard {
    @Resource private StringRedisTemplate redis;
    static final String RESERVE = """
            if redis.call('EXISTS', KEYS[1]) == 1 then
              if redis.call('HGET', KEYS[1], 'digest') ~= ARGV[1] then return 3 end
              return 2
            end
            if tonumber(redis.call('GET', KEYS[2]) or '0') >= tonumber(ARGV[2]) or
               tonumber(redis.call('GET', KEYS[3]) or '0') >= tonumber(ARGV[3]) then return 4 end
            redis.call('HSET', KEYS[1], 'digest', ARGV[1], 'state', 'PENDING', 'nonce', ARGV[4])
            redis.call('EXPIRE', KEYS[1], 86400)
            redis.call('INCR', KEYS[2]); redis.call('EXPIRE', KEYS[2], 172800)
            redis.call('INCR', KEYS[3]); redis.call('EXPIRE', KEYS[3], 120)
            return 1
            """;
    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(RESERVE, Long.class);
    private static final DefaultRedisScript<Long> FINISH = new DefaultRedisScript<>("""
            if redis.call('HGET', KEYS[1], 'state') ~= 'PENDING' or
               redis.call('HGET', KEYS[1], 'nonce') ~= ARGV[1] or
               redis.call('HGET', KEYS[1], 'digest') ~= ARGV[2] then return 0 end
            redis.call('HSET', KEYS[1], 'state', ARGV[3], 'result', ARGV[4])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<List> LOOKUP = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return {'RECEIPT_MISSING', '', ''} end
            if redis.call('HGET', KEYS[1], 'digest') ~= ARGV[1] then return {'STALE', '', ''} end
            return {redis.call('HGET', KEYS[1], 'state') or 'UNKNOWN',
                    redis.call('HGET', KEYS[1], 'result') or '',
                    redis.call('HGET', KEYS[1], 'nonce') or ''}
            """, List.class);
    public record Reservation(String key, String state, String result, String nonce, String digest) {
        public Reservation(String key, String state, String result) { this(key, state, result, "", ""); }
    }

    private String key(Long tenant, Long user, String requestId) {
        if (tenant == null || user == null) throw new IllegalArgumentException("Missing identity");
        return "rd:ai:{" + tenant + "}:request:" + user + ":" + UUID.fromString(requestId);
    }

    public Reservation lookup(Long tenant, Long user, String requestId, String digest) {
        return lookupKey(key(tenant, user, requestId), digest);
    }

    private Reservation lookupKey(String key, String digest) {
        List<?> stored = redis.execute(LOOKUP, List.of(key), digest);
        if (stored == null || stored.size() != 3) throw new IllegalStateException("Call guard unavailable");
        return new Reservation(key, String.valueOf(stored.get(0)), String.valueOf(stored.get(1)),
                String.valueOf(stored.get(2)), digest);
    }

    public Reservation reserve(Long tenant, Long user, String requestId, String digest, RdAiProperties config) {
        if (tenant == null || user == null) throw new IllegalArgumentException("Missing identity");
        String prefix = "rd:ai:{" + tenant + "}:";
        String key = key(tenant, user, requestId);
        String nonce = UUID.randomUUID().toString();
        String day = prefix + "day:" + LocalDate.now(ZoneId.of("Asia/Shanghai"));
        String minute = prefix + "minute:" + user + ":" + Instant.now().getEpochSecond() / 60;
        Long result = redis.execute(SCRIPT, List.of(key, day, minute), digest,
                Integer.toString(config.getDailyCallLimit()), Integer.toString(config.getPerUserMinuteLimit()), nonce);
        if (Long.valueOf(1).equals(result)) return new Reservation(key, "NEW", null, nonce, digest);
        if (Long.valueOf(3).equals(result)) return new Reservation(key, "STALE", null);
        if (Long.valueOf(4).equals(result)) return new Reservation(key, "LIMIT_REACHED", null);
        if (!Long.valueOf(2).equals(result)) throw new IllegalStateException("Call guard unavailable");
        return lookupKey(key, digest);
    }

    public void finish(Reservation reservation, String state, String result) {
        // A provider response cannot renew the 24-hour dedup window or release paid quota.
        if (!Long.valueOf(1).equals(redis.execute(FINISH, List.of(reservation.key()),
                reservation.nonce(), reservation.digest(), state, result))) {
            throw new IllegalStateException("Original request receipt unavailable");
        }
    }
}
