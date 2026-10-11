package com.ruiding.erp.service.ai;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.ruiding.erp.controller.admin.ai.vo.RdPackageAiAnswer;
import com.ruiding.erp.controller.admin.ai.vo.RdPackageAiAskReqVO;
import com.ruiding.erp.controller.admin.ai.vo.RdPackageAiContext;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class RdPackageAiService {
    @Resource private RdPackageAiFactsService facts;
    @Resource private RdAiProperties config;
    @Resource private RdAiCallGuard guard;
    @Resource private RdGlmClient client;
    private final ObjectMapper json = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Map<String, String> MESSAGES = Map.ofEntries(
            Map.entry("SUCCESS", "已取得GLM整理要点；数值与结论以ERP原记录为准。"),
            Map.entry("DISABLED", "AI真实调用未启用，ERP事实查询正常。"),
            Map.entry("UNCONFIGURED", "GLM密钥或模型尚未在服务器配置。"),
            Map.entry("COST_NOT_APPROVED", "GLM模型和费用尚未确认，未发送业务数据。"),
            Map.entry("LIMIT_NOT_SET", "调用次数或输出上限未正确设置，未发送业务数据。"),
            Map.entry("LIMIT_REACHED", "已达公司日调用或个人分钟限额，本次未请求模型。"),
            Map.entry("GUARD_UNAVAILABLE", "调用保护不可用，未发起新模型请求；原请求请保持待核。"),
            Map.entry("STALE", "原请求所依据的ERP记录已变化，请核对当前事实，不重发原请求。"),
            Map.entry("RECEIPT_MISSING", "原请求回执已过期或缺失，不能判定结果和费用，不重发原请求。"),
            Map.entry("PENDING", "原请求仍在处理或尚未取得回执，只能核对原请求，不要重复调用。"),
            Map.entry("UNKNOWN", "模型调用结果待核，可能已计费；不自动重试。"),
            Map.entry("FAILED", "模型未返回有效解读，不自动重试，ERP业务状态未变更。"),
            Map.entry("INVALID_OUTPUT", "模型输出未通过校验，未作为AI解读展示；ERP业务状态未变更。"));
    public record Stored(List<String> points, Integer totalTokens, String answeredAt) { }

    public RdPackageAiContext context(String code, Long user) { return facts.read(code, user).context(); }

    public RdPackageAiAnswer result(RdPackageAiAskReqVO request, Long user) {
        var snapshot = facts.read(request.getPackageCode(), user);
        RdAiCallGuard.Reservation receipt;
        try {
            receipt = guard.lookup(TenantContextHolder.getRequiredTenantId(), user, request.getRequestId(),
                    snapshot.digest() + ":" + request.getQuestion().name());
        } catch (Exception e) { return answer(request, "GUARD_UNAVAILABLE", snapshot.context(), null); }
        var current = facts.read(request.getPackageCode(), user);
        if (!snapshot.digest().equals(current.digest())) return answer(request, "STALE", current.context(), null);
        return replay(request, receipt, current.context());
    }

    public RdPackageAiAnswer ask(RdPackageAiAskReqVO request, Long user) {
        // Recheck current tenant, permission and authoritative facts before ANY cached answer.
        var snapshot = facts.read(request.getPackageCode(), user);
        var context = snapshot.context();
        String configured = config.state();
        if (!"READY".equals(configured)) return answer(request, configured, context, null);
        RdAiCallGuard.Reservation reserved;
        try {
            reserved = guard.reserve(TenantContextHolder.getRequiredTenantId(), user, request.getRequestId(),
                    snapshot.digest() + ":" + request.getQuestion().name(), config);
        } catch (Exception e) { return answer(request, "GUARD_UNAVAILABLE", context, null); }
        if (!"NEW".equals(reserved.state())) {
            return replay(request, reserved, context);
        }
        String state = "UNKNOWN";
        Stored stored = null;
        try {
            var result = client.ask(facts.export(snapshot, request.getQuestion().name()));
            // Validate again at the integration boundary, independent of transport implementation.
            if (result.points().isEmpty() || result.points().size() > 5
                    || result.points().stream().distinct().count() != result.points().size()
                    || result.points().stream().anyMatch(key -> context.notices().stream().noneMatch(n -> n.key().equals(key)))) {
                throw new RdGlmClient.Failure("INVALID_OUTPUT");
            }
            stored = new Stored(result.points(), result.totalTokens(), Instant.now().toString());
            state = "SUCCESS";
        } catch (RdGlmClient.Failure e) { state = e.state();
        } catch (Exception e) { state = "UNKNOWN"; }
        try { guard.finish(reserved, state, stored == null ? "" : json.writeValueAsString(stored));
        } catch (Exception e) { return answer(request, "UNKNOWN", context, null); }
        // Never present a completed model answer against changed ERP data/permissions.
        var current = facts.read(request.getPackageCode(), user);
        if (!snapshot.digest().equals(current.digest())) return answer(request, "STALE", current.context(), null);
        return answer(request, state, current.context(), stored);
    }

    private RdPackageAiAnswer replay(RdPackageAiAskReqVO request, RdAiCallGuard.Reservation receipt, RdPackageAiContext context) {
        Stored stored = null;
        try {
            if ("SUCCESS".equals(receipt.state())) {
                if (receipt.result() == null || receipt.result().length() > 4096) throw new IllegalArgumentException();
                stored = json.readValue(receipt.result(), Stored.class);
                if (stored.points() == null || stored.points().isEmpty() || stored.points().size() > 5
                        || stored.points().stream().distinct().count() != stored.points().size()
                        || (stored.totalTokens() != null && stored.totalTokens() < 0)
                        || stored.points().stream().anyMatch(key -> context.notices().stream().noneMatch(n -> n.key().equals(key)))) {
                    return answer(request, "INVALID_OUTPUT", context, null);
                }
                Instant.parse(stored.answeredAt());
            }
        } catch (Exception e) { return answer(request, "UNKNOWN", context, null); }
        return answer(request, receipt.state(), context, stored);
    }

    private RdPackageAiAnswer answer(RdPackageAiAskReqVO request, String state, RdPackageAiContext context, Stored stored) {
        if (!MESSAGES.containsKey(state)) state = "UNKNOWN";
        boolean success = "SUCCESS".equals(state) && stored != null;
        return new RdPackageAiAnswer(request.getRequestId(), state, MESSAGES.get(state), context,
                success ? stored.points() : List.of(), stored == null ? null : stored.totalTokens(), success ? stored.answeredAt() : null);
    }
}
