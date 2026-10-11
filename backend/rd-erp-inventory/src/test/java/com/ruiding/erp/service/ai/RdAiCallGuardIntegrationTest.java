package com.ruiding.erp.service.ai;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.redisson.Redisson;
import org.redisson.config.Config;
import org.redisson.api.RedissonClient;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "RD_AI_TEST_REDIS_PORT", matches = "6380")
class RdAiCallGuardIntegrationTest {
    private RedissonClient connection;
    private StringRedisTemplate redis;
    private RdAiCallGuard guard;
    private RdAiProperties config;
    private Long tenant;

    @BeforeEach void setup() {
        var settings = new Config();
        settings.useSingleServer().setAddress("redis://127.0.0.1:6380")
                .setPassword(System.getenv("RD_AI_TEST_REDIS_PASSWORD")).setRetryAttempts(0).setTimeout(2000);
        connection = Redisson.create(settings);
        redis = new StringRedisTemplate(new RedissonConnectionFactory(connection)); redis.afterPropertiesSet();
        guard = new RdAiCallGuard(); ReflectionTestUtils.setField(guard, "redis", redis);
        config = new RdAiProperties(); config.setDailyCallLimit(2); config.setPerUserMinuteLimit(2);
        tenant = 800_000_000L + java.util.concurrent.ThreadLocalRandom.current().nextInt(100_000_000);
    }
    @AfterEach void cleanup() {
        if (redis != null && tenant != null) {
            Set<String> keys = redis.keys("rd:ai:{" + tenant + "}:*");
            if (keys != null && !keys.isEmpty()) redis.delete(keys);
        }
        if (connection != null) connection.shutdown();
    }

    @Test void concurrentSameRequestReservesExactlyOneCallAndNeverRetriesUnknown() throws Exception {
        String id = UUID.randomUUID().toString();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var gate = new CountDownLatch(1);
            Callable<RdAiCallGuard.Reservation> op = () -> { gate.await(); return guard.reserve(tenant, 1L, id, "facts:STATUS", config); };
            var a = pool.submit(op); var b = pool.submit(op); gate.countDown();
            var results = List.of(a.get(5, TimeUnit.SECONDS), b.get(5, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(r -> r.state().equals("NEW")).count());
            var owner = results.stream().filter(r -> r.state().equals("NEW")).findFirst().orElseThrow();
            guard.finish(owner, "UNKNOWN", "");
            assertEquals("UNKNOWN", guard.reserve(tenant, 1L, id, "facts:STATUS", config).state());
            assertEquals("STALE", guard.reserve(tenant, 1L, id, "changed:STATUS", config).state());
            assertTrue(redis.getExpire(owner.key()) > 0);
        } finally { pool.shutdownNow(); }
    }

    @Test void globalDailyLimitIsSharedAcrossUsersAndReceiptReplayIsNotChargedAgain() {
        String id = UUID.randomUUID().toString();
        var first = guard.reserve(tenant, 1L, id, "facts", config);
        guard.finish(first, "SUCCESS", "controlled receipt");
        assertEquals("controlled receipt", guard.reserve(tenant, 1L, id, "facts", config).result());
        assertEquals("NEW", guard.reserve(tenant, 2L, UUID.randomUUID().toString(), "facts", config).state());
        assertEquals("LIMIT_REACHED", guard.reserve(tenant, 3L, UUID.randomUUID().toString(), "facts", config).state());
    }

    @Test void missingRequestCannotBeRecreatedByLateReceipt() {
        var first = guard.reserve(tenant, 1L, UUID.randomUUID().toString(), "facts", config);
        redis.delete(first.key());
        assertThrows(IllegalStateException.class, () -> guard.finish(first, "SUCCESS", "receipt"));
        assertFalse(Boolean.TRUE.equals(redis.hasKey(first.key())));
    }

    @Test void missingReceiptLookupNeverReservesOrChargesAnotherCall() {
        String id = UUID.randomUUID().toString();
        var first = guard.reserve(tenant, 1L, id, "facts", config);
        redis.delete(first.key());
        assertEquals("RECEIPT_MISSING", guard.lookup(tenant, 1L, id, "facts").state());
        assertFalse(Boolean.TRUE.equals(redis.hasKey(first.key())));
    }

    @Test void olderCompletionCannotOverwriteRecreatedReservation() {
        String id = UUID.randomUUID().toString();
        var first = guard.reserve(tenant, 1L, id, "facts-old", config);
        redis.delete(first.key());
        var newer = guard.reserve(tenant, 1L, id, "facts-new", config);
        assertEquals("NEW", newer.state());
        assertThrows(IllegalStateException.class, () -> guard.finish(first, "SUCCESS", "old receipt"));
        assertEquals("PENDING", guard.lookup(tenant, 1L, id, "facts-new").state());
        assertEquals("STALE", guard.lookup(tenant, 1L, id, "facts-old").state());
        guard.finish(newer, "SUCCESS", "new receipt");
        assertEquals("new receipt", guard.lookup(tenant, 1L, id, "facts-new").result());
    }
}
