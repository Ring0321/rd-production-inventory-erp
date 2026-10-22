package com.ruiding.erp.service.workassistant;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruiding.erp.dal.mysql.workassistant.RdWorkQueryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import static com.ruiding.erp.service.workassistant.RdWorkQueryContract.*;

@Service
public class RdWorkQueryService {
    private final RdWorkQueryAccessPolicy policy;
    private final RdWorkQueryMapper mapper;
    private final Clock clock;

    @Autowired public RdWorkQueryService(RdWorkQueryAccessPolicy policy, RdWorkQueryMapper mapper) {
        this(policy, mapper, Clock.systemUTC());
    }

    RdWorkQueryService(RdWorkQueryAccessPolicy policy, RdWorkQueryMapper mapper, Clock clock) {
        this.policy = policy;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 10)
    public Result<?> query(Request request) {
        var scope = policy.authorize(request);
        if (request.queryType() != Query.Q03 && request.queryType() != Query.Q04 && request.queryType() != Query.Q05) {
            throw new ServiceException(503, "This work query is not available yet");
        }
        Instant asOf = clock.instant();
        try {
            return request.queryType() == Query.Q04 ? inspections(request, scope, asOf) : packages(request, scope, asOf);
        } catch (DataAccessException exception) {
            return Result.unavailable(request, scope.dataScope(), "原业务数据暂不可用，请稍后重试或进入原页面核对。");
        }
    }

    private Result<PackageItem> packages(Request request, RdWorkQueryAccessPolicy.AccessScope scope, Instant asOf) {
        List<String> statuses = request.queryType() == Query.Q03 ? List.of("QC_PENDING") : List.of("QC_PASSED", "INBOUND_PENDING");
        var summary = mapper.selectPackageSummary(scope.tenantId(), statuses);
        List<PackageItem> items = mapper.selectPackages(scope.tenantId(), statuses, offset(request), request.pageSize()).stream().map(row -> {
            scope.requireSource(row.getTenantId(), null);
            return new PackageItem("PACKAGE_UNIT", row.getId().toString(), row.getPackageCode(), row.getStatus(),
                    validWeight(row.getPackageWeight()) ? row.getPackageWeight() : null, row.getVersion(), time(row.getUpdateTime()));
        }).toList();
        var metrics = new EnumMap<MetricKey, Metric>(MetricKey.class);
        metrics.put(MetricKey.DISTINCT_PACKAGE_COUNT, known(MetricKey.DISTINCT_PACKAGE_COUNT, summary.getPackageCount()));
        boolean complete = summary.getInvalidWeightCount() == 0;
        BigDecimal weight = summary.getPackageCount() == 0 ? BigDecimal.ZERO : summary.getRegisteredWeight();
        complete &= validWeight(weight);
        metrics.put(MetricKey.REGISTERED_WEIGHT_KG, complete
                ? new Metric(MetricKey.REGISTERED_WEIGHT_KG, Quality.KNOWN, weight, null)
                : new Metric(MetricKey.REGISTERED_WEIGHT_KG, Quality.UNAVAILABLE, null, "部分包件缺少有效登记重量"));
        if (request.queryType() == Query.Q05) {
            metrics.put(MetricKey.QC_PASSED_PACKAGE_COUNT, known(MetricKey.QC_PASSED_PACKAGE_COUNT, summary.getQcPassedCount()));
            metrics.put(MetricKey.INBOUND_PENDING_PACKAGE_COUNT, known(MetricKey.INBOUND_PENDING_PACKAGE_COUNT, summary.getInboundPendingCount()));
        }
        return new Result<>(request, scope.dataScope(), asOf, null, complete ? Quality.KNOWN : Quality.PARTIAL, metrics, items,
                complete ? summary.getPackageCount() : null, List.of(new SourceStamp(SourceType.PACKAGE_UNIT, null)),
                complete ? List.of() : List.of("包件数量已读取，登记重量待核；不把缺失重量填为零。"));
    }

    private Result<InspectionItem> inspections(Request request, RdWorkQueryAccessPolicy.AccessScope scope, Instant asOf) {
        var summary = mapper.selectInspectionSummary(scope.tenantId());
        Long covered = mapper.countCoveredPackages(scope.tenantId());
        var rows = mapper.selectInspections(scope.tenantId(), offset(request), request.pageSize());
        var preview = rows.isEmpty() ? List.<RdWorkQueryMapper.MemberRow>of()
                : mapper.selectMemberPreview(scope.tenantId(), rows.stream().map(RdWorkQueryMapper.InspectionRow::getId).toList());
        var items = rows.stream().map(row -> {
            scope.requireSource(row.getTenantId(), null);
            boolean coverageKnown = row.getCoveredPackageCount() > 0 && row.getInvalidMemberCount() == 0;
            List<MemberItem> members = preview.stream().filter(member -> member.getInspectionId().equals(row.getId()))
                    .map(member -> new MemberItem(member.getPackageId().toString(), member.getPackageCode(), Boolean.TRUE.equals(member.getSampled()))).toList();
            return new InspectionItem("QUALITY_INSPECTION", row.getId().toString(), row.getInspectionNo(), row.getStatus(),
                    time(row.getSubmitTime()), row.getVersion(), time(row.getUpdateTime()),
                    coverageKnown ? Quality.KNOWN : Quality.UNAVAILABLE,
                    coverageKnown ? row.getCoveredPackageCount().toString() : null,
                    coverageKnown ? row.getSampledPackageCount().toString() : null,
                    members, row.getCoveredPackageCount() > members.size());
        }).toList();
        boolean coverageKnown = summary.getInvalidCoverageCount() == 0;
        boolean complete = coverageKnown && summary.getMissingSubmitCount() == 0;
        var metrics = new EnumMap<MetricKey, Metric>(MetricKey.class);
        metrics.put(MetricKey.BUSINESS_OBJECT_COUNT, known(MetricKey.BUSINESS_OBJECT_COUNT, summary.getObjectCount()));
        metrics.put(MetricKey.DISTINCT_PACKAGE_COUNT, coverageKnown ? known(MetricKey.DISTINCT_PACKAGE_COUNT, covered)
                : new Metric(MetricKey.DISTINCT_PACKAGE_COUNT, Quality.UNAVAILABLE, null, "部分质检单缺少可靠的实际覆盖成员"));
        var notices = new ArrayList<String>();
        if (!coverageKnown) notices.add("部分质检单覆盖范围待核，不推断代表包、同炉或整批数量。");
        if (summary.getMissingSubmitCount() > 0) notices.add("部分已提交质检单缺少提交时间，不能据此计算等待时长。");
        return new Result<>(request, scope.dataScope(), asOf, null, complete ? Quality.KNOWN : Quality.PARTIAL, metrics, items,
                complete ? summary.getObjectCount() : null,
                List.of(new SourceStamp(SourceType.QUALITY_INSPECTION, null), new SourceStamp(SourceType.QUALITY_INSPECTION_MEMBER, null)), notices);
    }

    private static long offset(Request request) { return (long) (request.pageNo() - 1) * request.pageSize(); }
    private static Metric known(MetricKey key, Long value) { return new Metric(key, Quality.KNOWN, BigDecimal.valueOf(value), null); }
    private static boolean validWeight(BigDecimal value) { return value != null && value.signum() >= 0 && value.stripTrailingZeros().scale() <= 6; }
    private static String time(LocalDateTime value) { return value == null ? null : value.atZone(BUSINESS_ZONE).toInstant().toString(); }

    public record PackageItem(String sourceType, String sourceId, String packageCode, String status,
                              @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal registeredWeightKg,
                              Integer sourceVersion, String updatedAt) {}
    public record MemberItem(String packageId, String packageCode, boolean sampled) {}
    public record InspectionItem(String sourceType, String sourceId, String inspectionNo, String status, String submittedAt,
                                 Integer sourceVersion, String updatedAt, Quality coverageQuality,
                                 String coveredPackageCount, String sampledPackageCount,
                                 List<MemberItem> members, boolean membersTruncated) {
        public InspectionItem { members = List.copyOf(members); }
    }
}
