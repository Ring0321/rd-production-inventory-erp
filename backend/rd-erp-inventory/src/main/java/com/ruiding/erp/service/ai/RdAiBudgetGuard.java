package com.ruiding.erp.service.ai;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;

@Service
public class RdAiBudgetGuard {
    @Resource private StringRedisTemplate redis;
    private Clock clock = Clock.system(ZoneId.of("Asia/Shanghai"));
    private String prefix = "rd:ai-budget:{erp}:DEEPSEEK:";
    static final String RESERVE = """
            if redis.call('EXISTS', KEYS[2]) == 1 then return 3 end
            local used = tonumber(redis.call('GET', KEYS[1]) or '0')
            local limit = tonumber(ARGV[1])
            if not used or used < 0 or used ~= math.floor(used) or not limit or limit < 1 then return 3 end
            if used >= limit then return 2 end
            redis.call('INCR', KEYS[1]); redis.call('EXPIRE', KEYS[1], 6048000)
            redis.call('SET', KEYS[2], 'RESERVED', 'EX', 6048000)
            return 1
            """;
    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(RESERVE, Long.class);

    public String reserve(RdAiCallGuard.Reservation receipt, RdAiProperties config) {
        if (!config.deepSeek()) return "RESERVED";
        if (!"READY".equals(config.state()) || receipt.nonce() == null || receipt.nonce().isBlank())
            return "BUDGET_UNAVAILABLE";
        try {
            String month = prefix + YearMonth.now(clock);
            String marker = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((receipt.key() + ":" + receipt.nonce()).getBytes(StandardCharsets.UTF_8)));
            Long result = redis.execute(SCRIPT, List.of(month, month + ":request:" + marker),
                    Integer.toString(config.monthlyReservationLimit()));
            if (Long.valueOf(1).equals(result)) return "RESERVED";
            if (Long.valueOf(2).equals(result)) return "BUDGET_REACHED";
        } catch (Exception ignored) { }
        return "BUDGET_UNAVAILABLE";
    }
}
