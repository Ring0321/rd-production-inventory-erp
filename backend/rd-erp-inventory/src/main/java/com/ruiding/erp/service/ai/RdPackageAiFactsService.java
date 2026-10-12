package com.ruiding.erp.service.ai;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruiding.erp.controller.admin.ai.vo.RdPackageAiContext;
import com.ruiding.erp.controller.admin.ai.vo.RdPackageAiAskReqVO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.service.packageunit.RdPackageUnitService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static com.ruiding.erp.enums.ErrorCodeConstants.PACKAGE_NOT_EXISTS;

@Service
public class RdPackageAiFactsService {
    @Resource private PermissionApi permissionApi;
    @Resource private RdPackageUnitService packages;
    @Resource private RdAiProperties config;
    private static final Map<String, String> STAGES = Map.ofEntries(
            Map.entry("CREATED", "已建包"), Map.entry("LABELED", "已贴码"), Map.entry("QC_PENDING", "待质检"),
            Map.entry("QC_PASSED", "质检合格"), Map.entry("QC_REJECTED", "质检不合格"),
            Map.entry("INBOUND_PENDING", "待入库"), Map.entry("IN_STOCK", "在库"),
            Map.entry("STOCKTAKE_MISSING", "盘点缺失"), Map.entry("OUTBOUND_PENDING", "待出库"),
            Map.entry("OUTBOUNDED", "已出库"), Map.entry("VOIDED", "已作废"));
    private static final Set<String> QUALITY = Set.of("DRAFT", "SUBMITTED", "CORRECTED", "RESUBMITTED", "PASSED", "REJECTED");

    public record Snapshot(RdPackageAiContext context, String digest) { }

    @Transactional(readOnly = true)
    public Snapshot read(String code, Long userId) {
        TenantContextHolder.getRequiredTenantId();
        if (userId == null || !permissionApi.hasAnyPermissions(userId, "rd:package-unit:query")) throw exception(FORBIDDEN);
        if (code == null || !code.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")) throw new IllegalArgumentException("Invalid package identity");
        var trace = packages.getTraceDetail(code, true);
        if (trace == null || !code.equals(trace.getPackageCode()) || !Boolean.TRUE.equals(trace.getInternalView())) throw exception(PACKAGE_NOT_EXISTS);
        List<RdPackageUnitDO> members = trace.getProductionOutputId() == null ? List.of()
                : packages.getByProductionOutputId(trace.getProductionOutputId());
        boolean known = members != null && !members.isEmpty()
                && members.stream().allMatch(p -> p != null && p.getPackageCode() != null
                    && p.getPackageCode().matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")
                    && trace.getProductionOutputId().equals(p.getProductionOutputId()))
                && members.stream().anyMatch(p -> code.equals(p.getPackageCode()))
                && members.stream().map(RdPackageUnitDO::getPackageCode).distinct().count() == members.size();
        Map<String, Integer> counts = new TreeMap<>();
        BigDecimal weight = null;
        if (known) {
            for (var member : members) counts.merge(stage(member.getStatus()), 1, Integer::sum);
            if (members.stream().allMatch(p -> p.getPackageWeight() != null && p.getPackageWeight().signum() > 0)) {
                weight = members.stream().map(RdPackageUnitDO::getPackageWeight).reduce(BigDecimal.ZERO, BigDecimal::add);
            }
        }
        var source = new RdPackageAiContext.Source(known ? "KNOWN" : "UNKNOWN", known ? members.size() : null,
                weight, Collections.unmodifiableMap(counts));
        String status = stage(trace.getStatus());
        String quality = QUALITY.contains(Objects.toString(trace.getQualityStatus(), "")) ? trace.getQualityStatus() : "UNKNOWN";
        var notices = new ArrayList<RdPackageAiContext.Notice>();
        add(notices, "PACKAGE_STAGE", "当前包件登记状态：" + STAGES.getOrDefault(status, "未知，需核对原记录") + "。");
        if (Set.of("PASSED", "REJECTED").contains(quality) && quality.equals(trace.getQualityConclusion())) {
            add(notices, "QUALITY_REVIEWED", "本包覆盖的质检记录已审核，结论：" + ("PASSED".equals(quality) ? "合格。" : "不合格，不得据此安排正常入库或发货。"));
        } else if (Set.of("DRAFT", "SUBMITTED", "CORRECTED", "RESUBMITTED").contains(quality)) {
            add(notices, "QUALITY_PENDING", "质检记录尚未审核完成，待核不代表不合格；放行以质检岗位确认结果为准。");
        } else add(notices, "QUALITY_UNKNOWN", "未取得本包可确认的质检结果，不从同源其他包推断本包结论。");
        if (known) {
            add(notices, "SOURCE_SUMMARY", "同一生产记录已建包 " + members.size() + " 包，登记总重 "
                    + (weight == null ? "未知" : weight.toPlainString() + " kg") + "；包含单独列示的作废状态，不是计划量或实磅重量。");
            if (counts.size() > 1) add(notices, "SOURCE_MIXED_STATE", "同源包件状态不一致，批量办理仍需确认实际成员，不能整批自动过账。");
            if (counts.containsKey("VOIDED")) add(notices, "SOURCE_VOIDED", "同源记录中存在作废包，作废包不得参与入库或出库。");
        } else add(notices, "SOURCE_UNKNOWN", "同源包件汇总不可确认，不能把未知包数或重量视为零。");
        if ("IN_STOCK".equals(status) || counts.containsKey("IN_STOCK")) add(notices, "STOCK_NOT_AVAILABLE", "在库登记不等于订单可发库存，还需校验提货单、锁定占用和质检条件。");
        if (Set.of("QC_PASSED", "INBOUND_PENDING").contains(status)) add(notices, "WAREHOUSE_CONFIRMATION", "入库位置和最终入库由仓管员确认；司机仅按任务搬运、摆放，不更新库位或扫码过账。");
        if ("OUTBOUND_PENDING".equals(status)) add(notices, "OUTBOUND_REVIEW", "装车不等于出库完成，仓管员仍需按商务提货单和实际成员复核后确认出库。");
        if (Set.of("QC_REJECTED", "STOCKTAKE_MISSING", "VOIDED").contains(status)) add(notices, "EXCEPTION_REVIEW", "本包存在异常或作废状态，需按原业务流程核对处置，不根据AI建议自动放行。");
        add(notices, "READ_ONLY_AI", "解读仅供核对，不代替质检审核、库位确认、商务审批或仓管出入库过账。");
        var checked = RdPackageAiDiagnostics.inspect(members, known);
        var findings = checked.findings().stream().map(f -> new RdPackageAiContext.Finding(f.key(), f.severity(), f.owner(),
                f.title(), f.detail(), f.affectedCount(), f.packageCodes(), f.evidence())).toList();
        var diagnosis = new RdPackageAiContext.Diagnosis(checked.scope(), checked.state(), checked.inspectedPackages(), findings);
        for (var finding : findings) {
            String owner = Map.of("PRODUCTION", "生产登记岗", "QUALITY", "质检岗", "WAREHOUSE", "仓管员").get(finding.owner());
            add(notices, finding.key(), finding.title() + "：" + finding.detail() + " 核对岗位：" + owner
                    + "；影响包数：" + (finding.affectedCount() == null ? "未知" : finding.affectedCount()) + "。");
        }
        var context = new RdPackageAiContext(code, status, quality, source, Instant.now().toString(), config.state(), List.copyOf(notices), diagnosis);
        try {
            // Private identities remain local, including in the snapshot fingerprint; no raw value is cached.
            var serialized = new ObjectMapper().findAndRegisterModules().writeValueAsBytes(Arrays.asList("AI_DIAG_V1", trace, members));
            return new Snapshot(context, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(serialized)));
        } catch (Exception e) { throw new IllegalStateException("Unable to establish ERP snapshot"); }
    }

    public RdGlmClient.Input export(Snapshot snapshot, String question) {
        RdPackageAiAskReqVO.Question.valueOf(question);
        var c = snapshot.context();
        var source = c.source();
        return new RdGlmClient.Input(c.status(), c.qualityStage(), source.packageCount(),
                source.packageCount() != null && source.packageCount() > 1 ? source.recordedWeight() : null,
                source.counts(), question, c.notices().stream().map(RdPackageAiContext.Notice::key).toList());
    }

    private static String stage(String status) { return STAGES.containsKey(Objects.toString(status, "")) ? status : "UNKNOWN"; }
    private static void add(List<RdPackageAiContext.Notice> target, String key, String text) { target.add(new RdPackageAiContext.Notice(key, text)); }
}
