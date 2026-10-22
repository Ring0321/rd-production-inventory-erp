package com.ruiding.erp.service.workassistant;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Fixed read-only work-query boundary shared by authorized source readers. */
public final class RdWorkQueryContract {
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private RdWorkQueryContract() {}

    public enum Quality { KNOWN, PARTIAL, UNAVAILABLE }
    public enum DataScope { TENANT_AUTHORIZED, OWN_TASKS }
    public enum Unit { LEDGER_PACKAGE_QTY, PACKAGE, KG, BUSINESS_OBJECT, ALERT }
    public enum SourceType {
        STOCK_LOT, DAILY_STOCK_SUMMARY, PACKAGE_UNIT, QUALITY_INSPECTION, QUALITY_INSPECTION_MEMBER,
        PUTAWAY_TASK, PUTAWAY_TASK_LINE, LOADING_TASK, LOADING_TASK_LINE, LOADING_PACKAGE_CLAIM,
        WORK_ALERT, WORK_ALERT_SCAN_STATE, WORK_ALERT_EVENT
    }

    public enum MetricKey {
        LEDGER_IN_STOCK_QTY(Unit.LEDGER_PACKAGE_QTY, 6, false),
        LEDGER_AVAILABLE_QTY(Unit.LEDGER_PACKAGE_QTY, 6, false),
        LEDGER_LOCKED_QTY(Unit.LEDGER_PACKAGE_QTY, 6, false),
        REGISTERED_WEIGHT_KG(Unit.KG, 6, false),
        INBOUND_QTY(Unit.LEDGER_PACKAGE_QTY, 6, true),
        OUTBOUND_QTY(Unit.LEDGER_PACKAGE_QTY, 6, true),
        NET_CHANGE_QTY(Unit.LEDGER_PACKAGE_QTY, 6, true),
        DISTINCT_PACKAGE_COUNT(Unit.PACKAGE, 0, false),
        QC_PASSED_PACKAGE_COUNT(Unit.PACKAGE, 0, false),
        INBOUND_PENDING_PACKAGE_COUNT(Unit.PACKAGE, 0, false),
        BUSINESS_OBJECT_COUNT(Unit.BUSINESS_OBJECT, 0, false),
        ALERT_COUNT(Unit.ALERT, 0, false);

        private final Unit unit;
        private final int scale;
        private final boolean signed;

        MetricKey(Unit unit, int scale, boolean signed) {
            this.unit = unit;
            this.scale = scale;
            this.signed = signed;
        }
    }

    public enum Query {
        Q01(List.of("rd:dashboard:query"), Set.of(SourceType.STOCK_LOT),
                Set.of(MetricKey.LEDGER_IN_STOCK_QTY, MetricKey.LEDGER_AVAILABLE_QTY,
                        MetricKey.LEDGER_LOCKED_QTY, MetricKey.REGISTERED_WEIGHT_KG), null),
        Q02(List.of("rd:dashboard:query"), Set.of(SourceType.DAILY_STOCK_SUMMARY),
                Set.of(MetricKey.INBOUND_QTY, MetricKey.OUTBOUND_QTY, MetricKey.NET_CHANGE_QTY), null),
        Q03(List.of("rd:package-unit:query"), Set.of(SourceType.PACKAGE_UNIT),
                Set.of(MetricKey.DISTINCT_PACKAGE_COUNT, MetricKey.REGISTERED_WEIGHT_KG), MetricKey.DISTINCT_PACKAGE_COUNT),
        Q04(List.of("rd:quality-inspection:query"),
                Set.of(SourceType.QUALITY_INSPECTION, SourceType.QUALITY_INSPECTION_MEMBER),
                Set.of(MetricKey.BUSINESS_OBJECT_COUNT, MetricKey.DISTINCT_PACKAGE_COUNT), MetricKey.BUSINESS_OBJECT_COUNT),
        Q05(List.of("rd:package-unit:query"), Set.of(SourceType.PACKAGE_UNIT),
                Set.of(MetricKey.DISTINCT_PACKAGE_COUNT, MetricKey.QC_PASSED_PACKAGE_COUNT,
                        MetricKey.INBOUND_PENDING_PACKAGE_COUNT, MetricKey.REGISTERED_WEIGHT_KG), MetricKey.DISTINCT_PACKAGE_COUNT),
        Q06(List.of("rd:putaway-task:query"), Set.of(SourceType.PUTAWAY_TASK, SourceType.PUTAWAY_TASK_LINE),
                Set.of(MetricKey.BUSINESS_OBJECT_COUNT, MetricKey.DISTINCT_PACKAGE_COUNT), MetricKey.BUSINESS_OBJECT_COUNT),
        Q07(List.of("rd:loading-task:query"), Set.of(SourceType.LOADING_TASK, SourceType.LOADING_TASK_LINE),
                Set.of(MetricKey.BUSINESS_OBJECT_COUNT, MetricKey.DISTINCT_PACKAGE_COUNT), MetricKey.BUSINESS_OBJECT_COUNT),
        Q08(List.of("rd:loading-task:query", "rd:package-unit:query"),
                Set.of(SourceType.LOADING_PACKAGE_CLAIM, SourceType.LOADING_TASK, SourceType.PACKAGE_UNIT),
                Set.of(MetricKey.BUSINESS_OBJECT_COUNT, MetricKey.DISTINCT_PACKAGE_COUNT,
                        MetricKey.REGISTERED_WEIGHT_KG), MetricKey.DISTINCT_PACKAGE_COUNT),
        Q09(List.of("rd:work-alert:query"), Set.of(SourceType.WORK_ALERT, SourceType.WORK_ALERT_SCAN_STATE),
                Set.of(MetricKey.ALERT_COUNT, MetricKey.BUSINESS_OBJECT_COUNT, MetricKey.DISTINCT_PACKAGE_COUNT), MetricKey.ALERT_COUNT),
        Q10(List.of("rd:work-alert:query"), Set.of(SourceType.WORK_ALERT, SourceType.WORK_ALERT_EVENT),
                Set.of(MetricKey.ALERT_COUNT, MetricKey.BUSINESS_OBJECT_COUNT, MetricKey.DISTINCT_PACKAGE_COUNT), null);

        private final List<String> permissions;
        private final Set<SourceType> sourceTypes;
        private final Set<MetricKey> metricKeys;
        private final MetricKey totalKey;

        Query(List<String> permissions, Set<SourceType> sourceTypes, Set<MetricKey> metricKeys, MetricKey totalKey) {
            this.permissions = List.copyOf(permissions);
            this.sourceTypes = Set.copyOf(sourceTypes);
            this.metricKeys = Set.copyOf(metricKeys);
            this.totalKey = totalKey;
        }

        public List<String> permissions() { return permissions; }
        public Set<SourceType> sourceTypes() { return sourceTypes; }
        public Set<MetricKey> metricKeys() { return metricKeys; }
        public MetricKey totalKey() { return totalKey; }
        public boolean paged() { return totalKey != null; }
        public boolean reserved() { return this == Q09 || this == Q10; }
    }

    public record Request(Query queryType, String warehouseCode, Long alertId, int pageNo, int pageSize) {
        private static final Set<String> PARAMETERS = Set.of("queryType", "warehouseCode", "alertId", "pageNo", "pageSize");

        public Request {
            if (queryType == null || pageNo < 1 || pageNo > 10000 || pageSize < 1 || pageSize > 100
                    || warehouseCode != null && (warehouseCode.isBlank() || warehouseCode.length() > 64
                    || !warehouseCode.equals(warehouseCode.strip()) || warehouseCode.codePoints().anyMatch(Character::isISOControl))
                    || (queryType == Query.Q10) != (alertId != null) || alertId != null && alertId <= 0
                    || queryType == Query.Q10 && warehouseCode != null
                    || !queryType.paged() && (pageNo != 1 || pageSize != 20)) {
                throw badRequest();
            }
        }

        // The future HTTP adapter must pass all parameter values, not a collapsed single-value map.
        public static Request parse(Map<String, List<String>> parameters) {
            if (parameters == null || !PARAMETERS.containsAll(parameters.keySet()) || !parameters.containsKey("queryType")) {
                throw badRequest();
            }
            for (List<String> values : parameters.values()) {
                if (values == null || values.size() != 1 || values.get(0) == null || values.get(0).isBlank()) {
                    throw badRequest();
                }
            }
            try {
                return new Request(Query.valueOf(value(parameters, "queryType")), value(parameters, "warehouseCode"),
                        parameters.containsKey("alertId") ? positiveLong(value(parameters, "alertId")) : null,
                        parameters.containsKey("pageNo") ? Math.toIntExact(positiveLong(value(parameters, "pageNo"))) : 1,
                        parameters.containsKey("pageSize") ? Math.toIntExact(positiveLong(value(parameters, "pageSize"))) : 20);
            } catch (IllegalArgumentException | ArithmeticException exception) {
                throw badRequest();
            }
        }

        private static String value(Map<String, List<String>> parameters, String name) {
            return parameters.containsKey(name) ? parameters.get(name).get(0) : null;
        }

        private static long positiveLong(String value) {
            if (!value.matches("[1-9][0-9]{0,18}")) {
                throw badRequest();
            }
            return Long.parseLong(value);
        }
    }

    public record BusinessDay(LocalDate date, Instant startInclusive, Instant endExclusive) {
        public BusinessDay {
            require(date != null && date.atStartOfDay(BUSINESS_ZONE).toInstant().equals(startInclusive)
                    && date.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant().equals(endExclusive), "Invalid business day");
        }

        public static BusinessDay today(Clock clock) {
            LocalDate date = clock.instant().atZone(BUSINESS_ZONE).toLocalDate();
            return new BusinessDay(date, date.atStartOfDay(BUSINESS_ZONE).toInstant(), date.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant());
        }
    }

    public record Metric(MetricKey key, Quality quality,
                         @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal value, String reason) {
        public Metric {
            require(key != null && quality != null, "Metric identity is required");
            require(quality != Quality.KNOWN || value != null, "Known metric needs a value");
            require(quality != Quality.UNAVAILABLE || value == null, "Unavailable is not zero");
            require(quality == Quality.KNOWN || reason != null && !reason.isBlank(), "Incomplete metric needs a reason");
            if (value != null) {
                require((key.signed || value.signum() >= 0) && value.stripTrailingZeros().scale() <= key.scale,
                        "Invalid metric sign or precision");
            }
        }

        @JsonProperty("unit") public Unit unit() { return key.unit; }
    }

    /** Null revision explicitly means that the source has no reliable version token. */
    public record SourceStamp(SourceType type, String revision) {
        public SourceStamp {
            require(type != null && (revision == null || !revision.isBlank() && revision.length() <= 128), "Invalid source stamp");
        }
    }

    /** T is a query-specific read DTO, never a raw database entity or model-authored object. */
    public record Result<T>(Request request, DataScope scope,
                            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant asOf,
                            @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate businessDate, Quality quality,
                            Map<MetricKey, Metric> metrics, List<T> items, Long total,
                            List<SourceStamp> sources, List<String> notices) {
        public Result {
            require(request != null && scope != null && quality != null && metrics != null && items != null
                    && sources != null && notices != null, "Result fields are required");
            metrics = Map.copyOf(metrics);
            items = List.copyOf(items);
            sources = List.copyOf(sources);
            notices = List.copyOf(notices);
            Query query = request.queryType();
            require(scope != DataScope.OWN_TASKS || query == Query.Q06 || query == Query.Q07, "Invalid own-task query");
            require(metrics.keySet().equals(query.metricKeys())
                    && metrics.entrySet().stream().allMatch(entry -> entry.getKey() == entry.getValue().key()), "Unexpected metric keys");
            require(query == Query.Q02 ? quality == Quality.UNAVAILABLE || businessDate != null : businessDate == null,
                    "Business date is only for today's movements");
            require(notices.size() <= 20 && notices.stream().noneMatch(String::isBlank), "Invalid result notices");
            Set<SourceType> sourceTypes = sources.stream().map(SourceStamp::type).collect(Collectors.toSet());
            require(sourceTypes.size() == sources.size() && query.sourceTypes().containsAll(sourceTypes), "Unexpected source types");
            if (quality == Quality.UNAVAILABLE) {
                require(asOf == null && items.isEmpty() && total == null && sources.isEmpty()
                        && metrics.values().stream().allMatch(metric -> metric.quality() == Quality.UNAVAILABLE)
                        && !notices.isEmpty(), "Unavailable result must not expose facts");
            } else {
                require(asOf != null && !sources.isEmpty(), "Facts need a source snapshot");
                if (quality == Quality.KNOWN) {
                    require(sourceTypes.equals(query.sourceTypes())
                            && metrics.values().stream().allMatch(metric -> metric.quality() == Quality.KNOWN), "Known result must be complete");
                } else {
                    require(total == null && !notices.isEmpty(), "Partial result cannot claim an exact total");
                }
                if (query.paged()) {
                    require(items.size() <= request.pageSize(), "Page exceeds its requested size");
                    if (quality == Quality.KNOWN) {
                        long offset = (long) (request.pageNo() - 1) * request.pageSize();
                        require(total != null && total >= 0, "Known page needs an authorized total");
                        require(items.size() == Math.min(request.pageSize(), Math.max(total - offset, 0)), "Page does not match total");
                        require(metrics.get(query.totalKey()).value().compareTo(BigDecimal.valueOf(total)) == 0,
                                "Aggregate total must not be the page length");
                    }
                } else {
                    require(total == null && items.size() == (query == Query.Q10 ? 1 : 0), "Unexpected detail rows");
                }
            }
        }

        public static <T> Result<T> unavailable(Request request, DataScope scope, String reason) {
            var metrics = new EnumMap<MetricKey, Metric>(MetricKey.class);
            for (MetricKey key : request.queryType().metricKeys()) {
                metrics.put(key, new Metric(key, Quality.UNAVAILABLE, null, reason));
            }
            return new Result<>(request, scope, null, null, Quality.UNAVAILABLE, metrics, List.of(), null, List.of(), List.of(reason));
        }
    }

    private static ServiceException badRequest() { return new ServiceException(400, "Invalid work query request"); }
    private static void require(boolean valid, String message) {
        if (!valid) {
            throw new IllegalArgumentException(message);
        }
    }
}
