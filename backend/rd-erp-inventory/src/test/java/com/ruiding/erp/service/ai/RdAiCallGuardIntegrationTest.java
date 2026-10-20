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
    private String budgetPrefix;
    private RdAiBudgetGuard budget;

    @BeforeEach void setup() {
        var settings = new Config();
        settings.useSingleServer().setAddress("redis://127.0.0.1:6380")
                .setPassword(System.getenv("RD_AI_TEST_REDIS_PASSWORD")).setRetryAttempts(0).setTimeout(2000);
        connection = Redisson.create(settings);
        redis = new StringRedisTemplate(new RedissonConnectionFactory(connection)); redis.afterPropertiesSet();
        guard = new RdAiCallGuard(); ReflectionTestUtils.setField(guard, "redis", redis);
        config = new RdAiProperties(); config.setDailyCallLimit(2); config.setPerUserMinuteLimit(2);
        tenant = 800_000_000L + java.util.concurrent.ThreadLocalRandom.current().nextInt(100_000_000);
        budgetPrefix = "rd:ai-budget:{test-" + tenant + "}:";
        budget = new RdAiBudgetGuard(); ReflectionTestUtils.setField(budget, "redis", redis);
        ReflectionTestUtils.setField(budget, "prefix", budgetPrefix);
    }
    @AfterEach void cleanup() {
        if (redis != null && tenant != null) {
            Set<String> keys = redis.keys("rd:ai:{" + tenant + "}:*");
            if (keys != null && !keys.isEmpty()) redis.delete(keys);
            Set<String> monthly = redis.keys(budgetPrefix + "*");
            if (monthly != null && !monthly.isEmpty()) redis.delete(monthly);
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

    @Test void monthlyBudgetIsAtomicAcrossTenantsAndSurvivesNewServiceInstances() throws Exception {
        deepSeekReady(); config.setMonthlyBudgetCny(new java.math.BigDecimal("0.20"));
        var pool = Executors.newFixedThreadPool(5);
        try {
            var gate = new CountDownLatch(1);
            var tasks = new ArrayList<Future<String>>();
            for (int i = 0; i < 5; i++) {
                int identity = i;
                tasks.add(pool.submit(() -> { gate.await(); return budget.reserve(reservation(identity), config); }));
            }
            gate.countDown(); var results = new ArrayList<String>();
            for (var task : tasks) results.add(task.get(5, TimeUnit.SECONDS));
            assertEquals(2, results.stream().filter("RESERVED"::equals).count());
            assertEquals(3, results.stream().filter("BUDGET_REACHED"::equals).count());
            var restarted = new RdAiBudgetGuard();
            ReflectionTestUtils.setField(restarted, "redis", redis);
            ReflectionTestUtils.setField(restarted, "prefix", budgetPrefix);
            assertEquals("BUDGET_REACHED", restarted.reserve(reservation(99), config));
            String month = budgetPrefix + java.time.YearMonth.now(java.time.ZoneId.of("Asia/Shanghai"));
            assertEquals("2", redis.opsForValue().get(month));
            assertTrue(redis.getExpire(month) > 5_000_000);
        } finally { pool.shutdownNow(); }
    }

    @Test void monthlyGuardRefusesDuplicateReservationAndCorruptCounters() {
        deepSeekReady(); var receipt = reservation(1);
        assertEquals("RESERVED", budget.reserve(receipt, config));
        assertEquals("BUDGET_UNAVAILABLE", budget.reserve(receipt, config));
        String month = budgetPrefix + java.time.YearMonth.now(java.time.ZoneId.of("Asia/Shanghai"));
        assertEquals("1", redis.opsForValue().get(month));
        for (String bad : List.of("-1", "not-a-counter", "1.5")) {
            redis.opsForValue().set(month, bad);
            assertEquals("BUDGET_UNAVAILABLE", budget.reserve(reservation(2), config));
            assertEquals(bad, redis.opsForValue().get(month));
        }
    }

    @Test void monthlyPeriodFollowsShanghaiCalendarNotUtcOrUserIdentity() {
        deepSeekReady(); config.setMonthlyBudgetCny(new java.math.BigDecimal("0.10"));
        var zone = java.time.ZoneId.of("Asia/Shanghai");
        ReflectionTestUtils.setField(budget, "clock", java.time.Clock.fixed(java.time.Instant.parse("2026-10-31T15:59:59Z"), zone));
        assertEquals("RESERVED", budget.reserve(reservation(1), config));
        assertEquals("BUDGET_REACHED", budget.reserve(reservation(2), config));
        ReflectionTestUtils.setField(budget, "clock", java.time.Clock.fixed(java.time.Instant.parse("2026-10-31T16:00:00Z"), zone));
        assertEquals("RESERVED", budget.reserve(reservation(3), config));
        assertEquals("1", redis.opsForValue().get(budgetPrefix + "2026-10"));
        assertEquals("1", redis.opsForValue().get(budgetPrefix + "2026-11"));
    }

    private void deepSeekReady() {
        config.setEnabled(true); config.setProvider("DEEPSEEK"); config.setModel("deepseek-flash");
        config.setApiKey("not-a-real-credential"); config.setCostApproved(true);
        config.setDeepseekApiKey("deepseek-local-protocol-fixture");
        config.setMonthlyBudgetCny(new java.math.BigDecimal("20"));
        config.setPriceValidUntil(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(1).toString());
    }

    private RdAiCallGuard.Reservation reservation(int identity) {
        return new RdAiCallGuard.Reservation("test-tenant-" + identity + ":" + UUID.randomUUID(),
                "NEW", null, UUID.randomUUID().toString(), "digest");
    }
}
