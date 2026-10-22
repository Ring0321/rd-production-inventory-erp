package com.ruiding.erp.service.workassistant;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.ruiding.erp.service.workassistant.RdWorkQueryContract.*;
import static org.junit.jupiter.api.Assertions.*;

class RdWorkQueryContractTest {
    private static final Instant SNAPSHOT = Instant.parse("2026-10-22T01:00:00Z");

    @Test void parsesOnlyClosedQueryIdsAndDefaults() {
        Request request = Request.parse(Map.of("queryType", List.of("Q03")));
        assertEquals(Query.Q03, request.queryType());
        assertEquals(1, request.pageNo());
        assertEquals(20, request.pageSize());
        assertNull(request.warehouseCode());
        assertNull(request.alertId());
        assertThrows(ServiceException.class, () -> Request.parse(Map.of()));
        assertThrows(ServiceException.class,
                () -> Request.parse(Map.of("queryType", List.of("SELECT * FROM rd_package_unit"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"tenantId", "userId", "driverId", "role", "permissions", "sql", "question",
            "url", "model", "asOfDate", "startDate", "endDate", "warehouseName"})
    void rejectsClientIdentityInstructionsAndUnsupportedFilters(String key) {
        var exception = assertThrows(ServiceException.class, () -> Request.parse(
                Map.of("queryType", List.of("Q03"), key, List.of("1"))));
        assertEquals(400, exception.getCode());
    }

    @Test void rejectsRepeatedOrEmptyParametersRatherThanCollapsingThem() {
        for (List<String> values : List.of(List.of("1", "2"), List.<String>of(), List.of(""))) {
            assertThrows(ServiceException.class, () -> Request.parse(
                    Map.of("queryType", List.of("Q03"), "pageNo", values)));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "101", "2147483648", "1.5", " 20", "1e2", "all"})
    void boundsPageSizeAndRejectsAlternateNumericForms(String pageSize) {
        assertThrows(ServiceException.class, () -> Request.parse(
                Map.of("queryType", List.of("Q03"), "pageSize", List.of(pageSize))));
    }

    @Test void validatesTypedRequestsAsWellAsParameterMaps() {
        assertThrows(ServiceException.class, () -> new Request(Query.Q03, null, null, 0, 20));
        assertThrows(ServiceException.class, () -> new Request(Query.Q03, null, null, 10001, 20));
        assertThrows(ServiceException.class, () -> new Request(Query.Q03, " ", null, 1, 20));
        assertThrows(ServiceException.class, () -> new Request(Query.Q03, "A\nB", null, 1, 20));
        assertThrows(ServiceException.class, () -> new Request(Query.Q03, "A".repeat(65), null, 1, 20));
        assertThrows(ServiceException.class, () -> new Request(Query.Q10, null, null, 1, 20));
        assertThrows(ServiceException.class, () -> new Request(Query.Q10, null, 0L, 1, 20));
        assertThrows(ServiceException.class, () -> new Request(Query.Q03, null, 9L, 1, 20));
        assertThrows(ServiceException.class, () -> new Request(Query.Q01, null, null, 2, 20));
        assertThrows(ServiceException.class, () -> new Request(Query.Q10, "A", 9L, 1, 20));
        assertEquals(9L, Request.parse(Map.of("queryType", List.of("Q10"), "alertId", List.of("9"))).alertId());
        assertEquals("DEMO-SPACE-V2", new Request(Query.Q03, "DEMO-SPACE-V2", null, 1, 100).warehouseCode());
    }

    @Test void todayUsesShanghaiMidnightRegardlessOfJvmOrClockZone() {
        BusinessDay day = BusinessDay.today(Clock.fixed(Instant.parse("2026-10-21T16:00:00Z"), ZoneOffset.ofHours(-5)));
        assertEquals(LocalDate.of(2026, 10, 22), day.date());
        assertEquals(Instant.parse("2026-10-21T16:00:00Z"), day.startInclusive());
        assertEquals(Instant.parse("2026-10-22T16:00:00Z"), day.endExclusive());
        assertEquals(LocalDate.of(2026, 10, 21), BusinessDay.today(
                Clock.fixed(Instant.parse("2026-10-21T15:59:59.999Z"), ZoneOffset.UTC)).date());
    }

    @ParameterizedTest @EnumSource(Query.class)
    void everyQueryHasFrozenMetricsSourcesAndCapabilities(Query query) {
        assertFalse(query.metricKeys().isEmpty());
        assertFalse(query.sourceTypes().isEmpty());
        assertFalse(query.permissions().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> query.permissions().add("*"));
        assertThrows(UnsupportedOperationException.class, () -> query.metricKeys().clear());
    }

    @Test void capabilityNamesMatchOriginalReadControllers() {
        assertEquals(List.of("rd:dashboard:query"), Query.Q01.permissions());
        assertEquals(Query.Q01.permissions(), Query.Q02.permissions());
        assertEquals(List.of("rd:package-unit:query"), Query.Q03.permissions());
        assertEquals(List.of("rd:quality-inspection:query"), Query.Q04.permissions());
        assertEquals(Query.Q03.permissions(), Query.Q05.permissions());
        assertEquals(List.of("rd:putaway-task:query"), Query.Q06.permissions());
        assertEquals(List.of("rd:loading-task:query"), Query.Q07.permissions());
        assertEquals(List.of("rd:loading-task:query", "rd:package-unit:query"), Query.Q08.permissions());
        assertEquals(List.of("rd:work-alert:query"), Query.Q09.permissions());
        assertEquals(Query.Q09.permissions(), Query.Q10.permissions());
        assertTrue(Query.Q09.reserved());
        assertTrue(Query.Q10.reserved());
    }

    @Test void zeroPartialAndUnavailableHaveDifferentRepresentations() {
        Metric zero = new Metric(MetricKey.DISTINCT_PACKAGE_COUNT, Quality.KNOWN, BigDecimal.ZERO, null);
        assertEquals(BigDecimal.ZERO, zero.value());
        assertThrows(IllegalArgumentException.class,
                () -> new Metric(MetricKey.DISTINCT_PACKAGE_COUNT, Quality.KNOWN, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Metric(MetricKey.DISTINCT_PACKAGE_COUNT, Quality.UNAVAILABLE, BigDecimal.ZERO, "source failed"));
        assertThrows(IllegalArgumentException.class,
                () -> new Metric(MetricKey.DISTINCT_PACKAGE_COUNT, Quality.PARTIAL, BigDecimal.ONE, null));
        assertNull(new Metric(MetricKey.DISTINCT_PACKAGE_COUNT, Quality.UNAVAILABLE, null, "source failed").value());
    }

    @Test void preservesDecimalLedgerQuantitiesButRequiresIntegerIdentityCounts() throws Exception {
        Metric quantity = new Metric(MetricKey.LEDGER_IN_STOCK_QTY, Quality.KNOWN, new BigDecimal("0.125001"), null);
        assertEquals(Unit.LEDGER_PACKAGE_QTY, quantity.unit());
        assertThrows(IllegalArgumentException.class,
                () -> new Metric(MetricKey.DISTINCT_PACKAGE_COUNT, Quality.KNOWN, new BigDecimal("0.5"), null));
        assertThrows(IllegalArgumentException.class,
                () -> new Metric(MetricKey.REGISTERED_WEIGHT_KG, Quality.KNOWN, new BigDecimal("0.0000001"), null));
        assertThrows(IllegalArgumentException.class,
                () -> new Metric(MetricKey.REGISTERED_WEIGHT_KG, Quality.KNOWN, BigDecimal.ONE.negate(), null));
        assertEquals(new BigDecimal("-2"), new Metric(MetricKey.NET_CHANGE_QTY, Quality.KNOWN, new BigDecimal("-2"), null).value());
        assertEquals(new BigDecimal("-1"), new Metric(MetricKey.OUTBOUND_QTY, Quality.KNOWN, new BigDecimal("-1"), null).value());
        var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(
                new Metric(MetricKey.REGISTERED_WEIGHT_KG, Quality.KNOWN, new BigDecimal("9007199254740993.123456"), null)));
        assertTrue(json.get("value").isTextual());
        assertEquals("9007199254740993.123456", json.get("value").textValue());
        assertEquals("KG", json.get("unit").textValue());
    }

    @Test void knownResultRequiresCompleteMetricsAndRealSourceStampEvenWhenZero() {
        Request request = new Request(Query.Q03, null, null, 1, 20);
        assertThrows(IllegalArgumentException.class, () -> result(request, Quality.KNOWN, Map.of(), List.of(), 0L, sources(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> result(request, Quality.KNOWN, metrics(Quality.UNAVAILABLE), List.of(), 0L, sources(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> result(request, Quality.KNOWN, metrics(Quality.KNOWN), List.of(), 0L, List.of(), List.of()));
        Result<String> result = result(request, Quality.KNOWN, metrics(Quality.KNOWN), List.of(), 0L, sources(), List.of());
        assertEquals(Quality.KNOWN, result.quality());
        assertEquals(0L, result.total());
        assertEquals(SNAPSHOT, result.asOf());
    }

    @Test void pageSizeIsNotTotalAndIncompleteReadsCannotClaimAnExactTotal() {
        Request request = new Request(Query.Q03, null, null, 2, 2);
        var result = result(request, Quality.KNOWN, metrics(Quality.KNOWN), List.of("p3", "p4"), 5L, sources(), List.of());
        assertEquals(5L, result.total());
        assertEquals(2, result.items().size());
        assertThrows(IllegalArgumentException.class,
                () -> result(request, Quality.KNOWN, metrics(Quality.KNOWN), List.of("p3", "p4"), 2L, sources(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> result(request, Quality.KNOWN, metrics(Quality.KNOWN), List.of(), 5L, sources(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Result<>(request, DataScope.TENANT_AUTHORIZED,
                SNAPSHOT, null, Quality.KNOWN, metrics(Quality.KNOWN), List.of("p3", "p4"), 5L, sources(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> result(request, Quality.PARTIAL, metrics(Quality.PARTIAL), List.of("p3"), 5L, sources(), List.of("read incomplete")));
        assertNull(result(request, Quality.PARTIAL, metrics(Quality.PARTIAL), List.of("p3"), null, sources(), List.of("read incomplete")).total());
    }

    @Test void unavailableDoesNotExposeRowsNumbersSourcesOrSnapshot() {
        Request request = new Request(Query.Q03, null, null, 1, 20);
        Result<String> result = Result.unavailable(request, DataScope.TENANT_AUTHORIZED, "Source is not ready");
        assertNull(result.asOf());
        assertNull(result.total());
        assertTrue(result.items().isEmpty());
        assertTrue(result.sources().isEmpty());
        assertTrue(result.metrics().values().stream().allMatch(metric -> metric.value() == null));
        assertThrows(IllegalArgumentException.class,
                () -> result(request, Quality.UNAVAILABLE, metrics(Quality.UNAVAILABLE), List.of("p1"), null, sources(), List.of("failed")));
    }

    @Test void resultCopiesContainersAndRejectsForeignMetricsSourcesAndMissingDay() {
        Request request = new Request(Query.Q03, null, null, 1, 20);
        var mutableItems = new ArrayList<>(List.of("p1"));
        var mutableMetrics = metrics(Quality.KNOWN);
        var result = result(request, Quality.KNOWN, mutableMetrics, mutableItems, 1L, sources(), List.of());
        mutableItems.clear();
        mutableMetrics.clear();
        assertEquals(1, result.items().size());
        assertEquals(2, result.metrics().size());
        assertThrows(UnsupportedOperationException.class, () -> result.items().clear());
        assertThrows(IllegalArgumentException.class, () -> result(request, Quality.KNOWN,
                Map.of(MetricKey.ALERT_COUNT, new Metric(MetricKey.ALERT_COUNT, Quality.KNOWN, BigDecimal.ZERO, null)),
                List.of(), 0L, sources(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> result(request, Quality.KNOWN, metrics(Quality.KNOWN),
                List.of(), 0L, List.of(new SourceStamp(SourceType.LOADING_TASK, null)), List.of()));
    }

    @Test void todayMovementRequiresAnExplicitDayAndFactsRequireASnapshot() {
        var metrics = new EnumMap<MetricKey, Metric>(MetricKey.class);
        for (MetricKey key : Query.Q02.metricKeys()) {
            metrics.put(key, new Metric(key, Quality.KNOWN, BigDecimal.ZERO, null));
        }
        Request request = new Request(Query.Q02, null, null, 1, 20);
        var sources = List.of(new SourceStamp(SourceType.DAILY_STOCK_SUMMARY, null));
        assertThrows(IllegalArgumentException.class, () -> new Result<>(request,
                DataScope.TENANT_AUTHORIZED, SNAPSHOT, null, Quality.KNOWN, metrics, List.of(), null, sources, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Result<>(request,
                DataScope.TENANT_AUTHORIZED, null, LocalDate.of(2026, 10, 22), Quality.KNOWN, metrics, List.of(), null, sources, List.of()));
        assertDoesNotThrow(() -> new Result<>(request, DataScope.TENANT_AUTHORIZED, SNAPSHOT,
                LocalDate.of(2026, 10, 22), Quality.KNOWN, metrics, List.of(), null, sources, List.of()));
    }

    private static EnumMap<MetricKey, Metric> metrics(Quality quality) {
        var result = new EnumMap<MetricKey, Metric>(MetricKey.class);
        for (MetricKey key : Query.Q03.metricKeys()) {
            result.put(key, new Metric(key, quality, quality == Quality.UNAVAILABLE ? null : BigDecimal.ZERO,
                    quality == Quality.KNOWN ? null : "incomplete source"));
        }
        return result;
    }

    private static List<SourceStamp> sources() {
        return List.of(new SourceStamp(SourceType.PACKAGE_UNIT, null));
    }

    private static Result<String> result(Request request, Quality quality, Map<MetricKey, Metric> metrics,
                                          List<String> items, Long total, List<SourceStamp> sources, List<String> notices) {
        if (quality == Quality.KNOWN && total != null && metrics.containsKey(request.queryType().totalKey())
                && metrics.get(request.queryType().totalKey()).quality() == Quality.KNOWN) {
            metrics = new EnumMap<>(metrics);
            MetricKey totalKey = request.queryType().totalKey();
            metrics.put(totalKey, new Metric(totalKey, Quality.KNOWN, BigDecimal.valueOf(total), null));
        }
        return new Result<>(request, DataScope.TENANT_AUTHORIZED, SNAPSHOT, null, quality, metrics, items, total, sources, notices);
    }
}
