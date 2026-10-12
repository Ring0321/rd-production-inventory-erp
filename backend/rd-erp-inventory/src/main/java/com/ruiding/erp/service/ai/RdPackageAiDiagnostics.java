package com.ruiding.erp.service.ai;

import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.enums.RdPackageStatusEnum;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Local read-only rule checks. Findings and sample package identities must remain inside ERP. */
public final class RdPackageAiDiagnostics {

    private static final Set<String> KNOWN_STATUSES = Arrays.stream(RdPackageStatusEnum.values())
            .map(RdPackageStatusEnum::getStatus).collect(Collectors.toUnmodifiableSet());
    private static final int SAMPLE_LIMIT = 5;

    private RdPackageAiDiagnostics() { }

    public record Diagnosis(String scope, String state, Integer inspectedPackages, List<Finding> findings) {
        public Diagnosis {
            findings = List.copyOf(findings);
        }
    }

    public record Finding(String key, String severity, String owner, String title, String detail,
                          Integer affectedCount, List<String> packageCodes, String evidence) {
        public Finding {
            packageCodes = List.copyOf(packageCodes);
        }
    }

    /** The caller establishes source membership and permissions; this is not a batch or release decision. */
    public static Diagnosis inspect(List<RdPackageUnitDO> members, boolean sourceKnown) {
        if (!sourceKnown || members == null) return unknownSource();

        var affected = new EnumMap<Rule, Affected>(Rule.class);
        var locations = new HashSet<Long>();
        for (var member : members) {
            if (member == null) return unknownSource();
            String status = member.getStatus();
            if (status == null || !KNOWN_STATUSES.contains(status)) {
                add(affected, Rule.UNKNOWN_STATUS, member);
            } else {
                switch (status) {
                    case "VOIDED" -> add(affected, Rule.VOIDED, member);
                    case "QC_REJECTED" -> add(affected, Rule.QUALITY_REJECTED, member);
                    case "QC_PENDING" -> add(affected, Rule.QUALITY_PENDING, member);
                    case "STOCKTAKE_MISSING" -> add(affected, Rule.STOCKTAKE_MISSING, member);
                    default -> { }
                }
            }
            if (member.getPackageWeight() == null || member.getPackageWeight().signum() <= 0) {
                add(affected, Rule.INVALID_WEIGHT, member);
            }
            if (("IN_STOCK".equals(status) || "OUTBOUND_PENDING".equals(status))
                    && (member.getLocationId() == null || member.getLocationCode() == null
                    || member.getLocationCode().isBlank())) {
                add(affected, Rule.MISSING_LOCATION, member);
            }
            if (member.getLocationId() != null) {
                locations.add(member.getLocationId());
                add(affected, Rule.SPLIT_LOCATION, member);
            }
        }
        if (locations.size() <= 1) affected.remove(Rule.SPLIT_LOCATION);

        var findings = new ArrayList<Finding>();
        affected.forEach((rule, count) -> findings.add(rule.finding(count.count, List.copyOf(count.samples))));
        return new Diagnosis("PRODUCTION_OUTPUT", "KNOWN", members.size(), findings);
    }

    private static Diagnosis unknownSource() {
        return new Diagnosis("PRODUCTION_OUTPUT", "UNKNOWN", null,
                List.of(Rule.SOURCE_UNKNOWN.finding(null, List.of())));
    }

    private static void add(EnumMap<Rule, Affected> affected, Rule rule, RdPackageUnitDO member) {
        var count = affected.computeIfAbsent(rule, ignored -> new Affected());
        count.count++;
        String code = member.getPackageCode();
        if (count.samples.size() < SAMPLE_LIMIT && code != null && !code.isBlank()) count.samples.add(code);
    }

    private static final class Affected {
        private int count;
        private final Set<String> samples = new LinkedHashSet<>();
    }

    private enum Rule {
        SOURCE_UNKNOWN("REVIEW", "PRODUCTION", "同源包件来源待核",
                "未取得可确认的同一生产产出记录包件，包数与诊断结果保持未知，不按零处理。",
                "依据：同一生产产出记录的来源确认状态。"),
        VOIDED("HIGH", "PRODUCTION", "存在作废包件",
                "登记状态包含作废包件，需由生产岗位按原业务流程核对处置；本项不自动改变库存。",
                "依据：包件登记状态为作废。"),
        QUALITY_REJECTED("HIGH", "QUALITY", "存在质检不合格登记",
                "登记状态包含质检不合格包件，需由质检岗位核对原质检记录；不据同源其他包件推断本包结论。",
                "依据：包件登记状态为质检不合格。"),
        QUALITY_PENDING("REVIEW", "QUALITY", "存在待质检包件",
                "登记状态包含待质检包件，待检不等于不合格或已放行，需由质检岗位核对实际结果。",
                "依据：包件登记状态为待质检。"),
        STOCKTAKE_MISSING("HIGH", "WAREHOUSE", "存在盘点缺失登记",
                "登记状态包含盘点缺失包件，需由仓管岗位核对实物与原盘点记录；本项不自动调整库存。",
                "依据：包件登记状态为盘点缺失。"),
        UNKNOWN_STATUS("REVIEW", "PRODUCTION", "存在未知登记状态",
                "部分包件未取得可识别的登记状态，需由生产岗位核对原记录，不能据此判断可发量。",
                "依据：包件登记状态缺失或不在受控状态范围内。"),
        INVALID_WEIGHT("REVIEW", "PRODUCTION", "登记重量缺失或无效",
                "部分包件登记重量缺失或不大于零，需由生产岗位核对原登记；不补算重量或假设单包吨数。",
                "依据：包件登记重量为空、为零或为负数。"),
        MISSING_LOCATION("REVIEW", "WAREHOUSE", "在库或待出库包件库位缺失",
                "部分在库或待出库包件缺少库位编号或库位代码，需由仓管岗位核对原记录；本项不自动确认位置。",
                "依据：在库或待出库登记的库位编号为空，或库位代码为空白。"),
        SPLIT_LOCATION("INFO", "WAREHOUSE", "包件分散登记于多个库位",
                "同一生产产出记录中有库位编号的包件分布在多个库位，仅作交接信息提示，不视为错误或放行依据。",
                "依据：包件登记中存在多个不同的非空库位编号。");

        private final String severity;
        private final String owner;
        private final String title;
        private final String detail;
        private final String evidence;

        Rule(String severity, String owner, String title, String detail, String evidence) {
            this.severity = severity;
            this.owner = owner;
            this.title = title;
            this.detail = detail;
            this.evidence = evidence;
        }

        private Finding finding(Integer count, List<String> samples) {
            return new Finding("DIAG_" + name(), severity, owner, title, detail, count, samples, evidence);
        }
    }
}
