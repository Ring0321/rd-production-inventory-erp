package com.ruiding.erp.service.packageunit;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.ruiding.erp.controller.admin.packageunit.vo.RdPackageRelationsRespVO;
import com.ruiding.erp.controller.admin.packageunit.vo.RdPackageRelationsRespVO.Edge;
import com.ruiding.erp.controller.admin.packageunit.vo.RdPackageRelationsRespVO.Node;
import com.ruiding.erp.controller.admin.packageunit.vo.RdPackageRelationsRespVO.Section;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingTaskDO;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingTaskLineDO;
import com.ruiding.erp.dal.dataobject.masterdata.RdWarehouseLocationDO;
import com.ruiding.erp.dal.dataobject.outbound.RdOutboundOrderDO;
import com.ruiding.erp.dal.dataobject.outbound.RdOutboundOrderLineDO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageTaskEventDO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.production.RdProductionOutputDO;
import com.ruiding.erp.dal.dataobject.production.RdProductionTaskDO;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayTaskDO;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayTaskLineDO;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionDO;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionMemberDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockTransactionDO;
import com.ruiding.erp.dal.mysql.loading.RdLoadingTaskLineMapper;
import com.ruiding.erp.dal.mysql.loading.RdLoadingTaskMapper;
import com.ruiding.erp.dal.mysql.masterdata.RdWarehouseLocationMapper;
import com.ruiding.erp.dal.mysql.outbound.RdOutboundOrderLineMapper;
import com.ruiding.erp.dal.mysql.outbound.RdOutboundOrderMapper;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageTaskEventMapper;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.dal.mysql.production.RdProductionOutputMapper;
import com.ruiding.erp.dal.mysql.production.RdProductionTaskMapper;
import com.ruiding.erp.dal.mysql.putaway.RdPutawayTaskLineMapper;
import com.ruiding.erp.dal.mysql.putaway.RdPutawayTaskMapper;
import com.ruiding.erp.dal.mysql.quality.RdQualityInspectionMapper;
import com.ruiding.erp.dal.mysql.quality.RdQualityInspectionMemberMapper;
import com.ruiding.erp.dal.mysql.stock.RdStockTransactionMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.ruiding.erp.enums.ErrorCodeConstants.PACKAGE_NOT_EXISTS;

@Service
public class RdPackageRelationsService {
    private static final int ROW_LIMIT = 100;
    private static final int NODE_LIMIT = 500;
    private static final int EDGE_LIMIT = 1000;
    private static final String GLOBAL_LIMIT = "GLOBAL_LIMIT_500_NODES_1000_EDGES";
    @Resource private PermissionApi permissionApi;
    @Resource private RdPackageUnitMapper packageMapper;
    @Resource private RdProductionOutputMapper outputMapper;
    @Resource private RdProductionTaskMapper productionTaskMapper;
    @Resource private RdQualityInspectionMapper qualityMapper;
    @Resource private RdQualityInspectionMemberMapper qualityMemberMapper;
    @Resource private RdPutawayTaskMapper putawayTaskMapper;
    @Resource private RdPutawayTaskLineMapper putawayLineMapper;
    @Resource private RdLoadingTaskMapper loadingTaskMapper;
    @Resource private RdLoadingTaskLineMapper loadingLineMapper;
    @Resource private RdPackageTaskEventMapper eventMapper;
    @Resource private RdWarehouseLocationMapper locationMapper;
    @Resource private RdOutboundOrderMapper outboundMapper;
    @Resource private RdOutboundOrderLineMapper outboundLineMapper;
    @Resource private RdStockTransactionMapper transactionMapper;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public RdPackageRelationsRespVO getRelations(String packageCode, Long userId) {
        if (userId == null || !permissionApi.hasAnyPermissions(userId, "rd:package-unit:query")) {
            throw new AccessDeniedException("Package query permission required");
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (packageCode == null || packageCode.isBlank() || packageCode.trim().length() > 128) {
            throw new IllegalArgumentException("Invalid package code");
        }
        String code = packageCode.trim();
        var rootQuery = query(RdPackageUnitDO.class, RdPackageUnitDO::getId, tenantId)
                .eq(RdPackageUnitDO::getPackageCode, code).last("LIMIT 1");
        RdPackageUnitDO root = packageMapper.selectList(rootQuery).stream()
                .filter(p -> visible(p, tenantId) && p.getId() != null && code.equals(p.getPackageCode()))
                .findFirst().orElseThrow(() -> exception(PACKAGE_NOT_EXISTS));
        Graph g = new Graph(tenantId, root);
        g.authorize("PRODUCTION_OUTPUT", "rd:production-output:query", userId);
        g.authorize("PRODUCTION_TASK", "rd:production-task:query", userId);
        g.authorize("QUALITY", "rd:quality-inspection:query", userId);
        g.authorize("PUTAWAY", "rd:putaway-task:query", userId);
        g.authorize("LOADING", "rd:loading-task:query", userId);
        g.authorize("POSITION", "rd:warehouse-location:query", userId);
        g.authorize("OUTBOUND", "rd:outbound-order:query", userId);
        g.authorize("STOCK_TRANSACTION", "rd:stock-transaction:query", userId);
        production(g);
        quality(g);
        putaway(g);
        loading(g);
        outbound(g);
        stock(g);
        positions(g);
        return g.response();
    }

    private void production(Graph g) {
        RdProductionOutputDO output = null;
        Section s = g.sections.get("PRODUCTION_OUTPUT");
        if (g.enabled(s) && g.root.getProductionOutputId() != null) {
            output = one(outputMapper, RdProductionOutputDO.class, RdProductionOutputDO::getId,
                    g.root.getProductionOutputId(), g.tenantId);
            if (output == null) incomplete(s);
            else {
                String key = g.node("PRODUCTION_OUTPUT", output.getId(), "Production output " + output.getId(), output.getStatus(), s);
                g.edge(g.rootKey, key, "PRODUCTION_SOURCE", "PACKAGE_UNIT", g.root.getId(), s)
                        .setSourceVersion(g.root.getVersion());
                for (var member : many(packageMapper, query(RdPackageUnitDO.class, RdPackageUnitDO::getId, g.tenantId)
                        .eq(RdPackageUnitDO::getProductionOutputId, output.getId()), g, s)) {
                    if (!Objects.equals(member.getProductionOutputId(), output.getId())) { incomplete(s); continue; }
                    String peer = g.packageNode(member.getId(), member.getPackageCode(), s);
                    g.edge(key, peer, "OUTPUT_MEMBER", "PACKAGE_UNIT", member.getId(), s).setSourceVersion(member.getVersion());
                }
            }
        }
        s = g.sections.get("PRODUCTION_TASK");
        if (!g.enabled(s)) return;
        Long taskId = g.root.getProductionTaskId();
        if (taskId == null && output != null) taskId = output.getProductionTaskId();
        if (taskId == null) {
            if (g.root.getProductionOutputId() != null && output == null) incomplete(s);
            return;
        }
        var task = one(productionTaskMapper, RdProductionTaskDO.class, RdProductionTaskDO::getId, taskId, g.tenantId);
        if (task == null) { incomplete(s); return; }
        String key = g.node("PRODUCTION_TASK", task.getId(), task.getTaskNo(), task.getStatus(), s);
        if (Objects.equals(g.root.getProductionTaskId(), taskId)) {
            g.edge(g.rootKey, key, "PRODUCTION_TASK_SOURCE", "PACKAGE_UNIT", g.root.getId(), s)
                    .setSourceVersion(g.root.getVersion());
        }
        if (output != null && Objects.equals(output.getProductionTaskId(), taskId)) {
            g.edge(g.key("PRODUCTION_OUTPUT", output.getId()), key, "OUTPUT_TASK_SOURCE", "PRODUCTION_OUTPUT", output.getId(), s)
                    .setSourceVersion(output.getVersion());
        } else if (output != null && output.getProductionTaskId() != null) incomplete(s);
    }

    private void quality(Graph g) {
        Section s = g.sections.get("QUALITY");
        if (!g.enabled(s)) return;
        // Review releases activeKey; persisted packageId membership remains the coverage evidence.
        var rootMembers = many(qualityMemberMapper, query(RdQualityInspectionMemberDO.class, RdQualityInspectionMemberDO::getId, g.tenantId)
                .eq(RdQualityInspectionMemberDO::getPackageId, g.root.getId()), g, s).stream()
                .filter(m -> g.matchesRoot(m.getPackageId(), m.getPackageCode(), s)).toList();
        var representatives = many(qualityMapper, query(RdQualityInspectionDO.class, RdQualityInspectionDO::getId, g.tenantId)
                .eq(RdQualityInspectionDO::getPackageId, g.root.getId()), g, s);
        Set<Long> ids = new TreeSet<>();
        rootMembers.stream().map(RdQualityInspectionMemberDO::getInspectionId).filter(Objects::nonNull).forEach(ids::add);
        representatives.stream().filter(q -> Objects.equals(q.getPackageId(), g.root.getId()))
                .map(RdQualityInspectionDO::getId).filter(Objects::nonNull).forEach(ids::add);
        int groups = 0;
        for (Long id : ids) {
            if (++groups > ROW_LIMIT) { limited(s, "ROW_LIMIT_100"); break; }
            if (!g.enabled(s)) break;
            var inspection = one(qualityMapper, RdQualityInspectionDO.class, RdQualityInspectionDO::getId, id, g.tenantId);
            if (inspection == null) { incomplete(s); continue; }
            var members = many(qualityMemberMapper, query(RdQualityInspectionMemberDO.class, RdQualityInspectionMemberDO::getId, g.tenantId)
                    .eq(RdQualityInspectionMemberDO::getInspectionId, id), g, s).stream()
                    .filter(m -> Objects.equals(m.getInspectionId(), id)).toList();
            var rootEvidence = rootMembers.stream().filter(m -> Objects.equals(m.getInspectionId(), id)).toList();
            if (members.isEmpty() && rootEvidence.isEmpty() && Objects.equals(inspection.getPackageId(), g.root.getId())
                    && (inspection.getClientRequestId() != null || inspection.getRegistrarId() != null)) {
                incomplete(s);
                continue;
            }
            boolean legacy = members.isEmpty() && rootEvidence.isEmpty() && Objects.equals(inspection.getPackageId(), g.root.getId());
            if (!legacy && rootEvidence.isEmpty() && members.stream().noneMatch(m -> Objects.equals(m.getPackageId(), g.root.getId()))) {
                incomplete(s);
                continue;
            }
            String key = g.node("QUALITY_INSPECTION", id, inspection.getInspectionNo(), inspection.getStatus(), s);
            if (legacy) {
                g.edge(key, g.rootKey, "LEGACY_REPRESENTATIVE", "QUALITY_INSPECTION", id, s)
                        .setSourceVersion(inspection.getVersion());
                continue;
            }
            Map<Long, RdQualityInspectionMemberDO> evidence = new LinkedHashMap<>();
            members.forEach(m -> evidence.put(m.getId(), m));
            rootEvidence.forEach(m -> evidence.putIfAbsent(m.getId(), m));
            for (var member : evidence.values()) {
                String peer = g.packageNode(member.getPackageId(), member.getPackageCode(), s);
                g.edge(key, peer, "QUALITY_COVERAGE", "QUALITY_INSPECTION_MEMBER", member.getId(), s)
                        .setSampled(member.getSampled()).setSourceVersion(inspection.getVersion());
            }
        }
    }

    private void putaway(Graph g) {
        Section s = g.sections.get("PUTAWAY");
        if (!g.enabled(s)) return;
        var rootLines = many(putawayLineMapper, query(RdPutawayTaskLineDO.class, RdPutawayTaskLineDO::getId, g.tenantId)
                .eq(RdPutawayTaskLineDO::getPackageId, g.root.getId()), g, s).stream()
                .filter(l -> g.matchesRoot(l.getPackageId(), l.getPackageCode(), s)).toList();
        var rootEvents = rootEvents(g, "PUTAWAY", s);
        Set<Long> taskIds = taskIds(rootLines.stream().map(RdPutawayTaskLineDO::getTaskId).toList(), rootEvents, "PUTAWAY_TASK");
        for (Long id : taskIds) {
            if (!g.enabled(s)) break;
            var task = one(putawayTaskMapper, RdPutawayTaskDO.class, RdPutawayTaskDO::getId, id, g.tenantId);
            if (task == null) { incomplete(s); continue; }
            String key = g.node("PUTAWAY_TASK", id, task.getTaskNo(), task.getStatus(), s);
            g.putawayTasks.put(id, task);
            var lines = many(putawayLineMapper, query(RdPutawayTaskLineDO.class, RdPutawayTaskLineDO::getId, g.tenantId)
                    .eq(RdPutawayTaskLineDO::getTaskId, id), g, s);
            Map<Long, RdPutawayTaskLineDO> evidence = new LinkedHashMap<>();
            lines.stream().filter(l -> Objects.equals(l.getTaskId(), id)).forEach(l -> evidence.put(l.getId(), l));
            rootLines.stream().filter(l -> Objects.equals(l.getTaskId(), id)).forEach(l -> evidence.putIfAbsent(l.getId(), l));
            for (var line : evidence.values()) {
                String peer = g.packageNode(line.getPackageId(), line.getPackageCode(), s);
                String relation = "CANCELLED".equals(line.getLineStatus()) ? "HISTORICAL_PUTAWAY_MEMBER" : "PUTAWAY_MEMBER";
                Edge edge = g.edge(key, peer, relation, "PUTAWAY_TASK_LINE", line.getId(), s)
                        .setLineStatus(line.getLineStatus()).setSourceVersion(line.getVersion());
                if (g.edges.containsKey(edge.getEdgeKey())) g.putawayLines.put(line.getId(), line);
            }
            taskEvents(g, id, "PUTAWAY_TASK", key, s);
        }
        rootEvents.forEach(e -> event(g, e, "PUTAWAY_TASK".equals(e.getSourceType()) ? g.key("PUTAWAY_TASK", e.getSourceId()) : null, s));
    }

    private void loading(Graph g) {
        Section s = g.sections.get("LOADING");
        if (!g.enabled(s)) return;
        var rootLines = many(loadingLineMapper, query(RdLoadingTaskLineDO.class, RdLoadingTaskLineDO::getId, g.tenantId)
                .eq(RdLoadingTaskLineDO::getPackageId, g.root.getId()), g, s).stream()
                .filter(l -> g.matchesRoot(l.getPackageId(), l.getPackageCode(), s)).toList();
        var rootEvents = rootEvents(g, "LOADING", s);
        // Only TASK sources carry task IDs. SCAN and EXCEPTION IDs belong to their own tables.
        Set<Long> taskIds = taskIds(rootLines.stream().map(RdLoadingTaskLineDO::getTaskId).toList(), rootEvents, "LOADING_TASK");
        for (Long id : taskIds) {
            if (!g.enabled(s)) break;
            var task = one(loadingTaskMapper, RdLoadingTaskDO.class, RdLoadingTaskDO::getId, id, g.tenantId);
            if (task == null) { incomplete(s); continue; }
            String key = g.node("LOADING_TASK", id, task.getTaskNo(), task.getStatus(), s);
            var lines = many(loadingLineMapper, query(RdLoadingTaskLineDO.class, RdLoadingTaskLineDO::getId, g.tenantId)
                    .eq(RdLoadingTaskLineDO::getTaskId, id), g, s);
            Map<Long, RdLoadingTaskLineDO> evidence = new LinkedHashMap<>();
            lines.stream().filter(l -> Objects.equals(l.getTaskId(), id)).forEach(l -> evidence.put(l.getId(), l));
            rootLines.stream().filter(l -> Objects.equals(l.getTaskId(), id)).forEach(l -> evidence.putIfAbsent(l.getId(), l));
            for (var line : evidence.values()) {
                String peer = g.packageNode(line.getPackageId(), line.getPackageCode(), s);
                String relation = "CANCELLED".equals(line.getLineStatus()) ? "HISTORICAL_LOADING_MEMBER" : "LOADING_MEMBER";
                Edge edge = g.edge(key, peer, relation, "LOADING_TASK_LINE", line.getId(), s)
                        .setLineStatus(line.getLineStatus()).setSourceVersion(line.getVersion());
                if (g.edges.containsKey(edge.getEdgeKey())) g.loadingLines.put(line.getId(), line);
            }
            taskEvents(g, id, "LOADING_TASK", key, s);
        }
        rootEvents.forEach(e -> event(g, e, "LOADING_TASK".equals(e.getSourceType()) ? g.key("LOADING_TASK", e.getSourceId()) : null, s));
    }

    private List<RdPackageTaskEventDO> rootEvents(Graph g, String domain, Section s) {
        return many(eventMapper, query(RdPackageTaskEventDO.class, RdPackageTaskEventDO::getId, g.tenantId)
                .eq(RdPackageTaskEventDO::getPackageId, g.root.getId())
                .in(RdPackageTaskEventDO::getSourceType, List.of(domain + "_TASK", domain + "_SCAN", domain + "_EXCEPTION")), g, s)
                .stream().filter(e -> g.matchesRoot(e.getPackageId(), e.getPackageCode(), s))
                .filter(e -> e.getSourceType() != null && e.getSourceType().startsWith(domain + "_")).toList();
    }

    private Set<Long> taskIds(List<Long> lineIds, List<RdPackageTaskEventDO> events, String sourceType) {
        Set<Long> ids = new TreeSet<>();
        lineIds.stream().filter(Objects::nonNull).forEach(ids::add);
        events.stream().filter(e -> sourceType.equals(e.getSourceType())).map(RdPackageTaskEventDO::getSourceId)
                .filter(Objects::nonNull).forEach(ids::add);
        return ids;
    }

    private void taskEvents(Graph g, Long taskId, String sourceType, String taskKey, Section s) {
        for (var e : many(eventMapper, query(RdPackageTaskEventDO.class, RdPackageTaskEventDO::getId, g.tenantId)
                .eq(RdPackageTaskEventDO::getSourceType, sourceType).eq(RdPackageTaskEventDO::getSourceId, taskId), g, s)) {
            if (sourceType.equals(e.getSourceType()) && Objects.equals(e.getSourceId(), taskId)) event(g, e, taskKey, s);
            else incomplete(s);
        }
    }

    private void event(Graph g, RdPackageTaskEventDO e, String taskKey, Section s) {
        String peer = g.packageNode(e.getPackageId(), e.getPackageCode(), s);
        if (peer == null) return;
        String key = g.node("TASK_EVENT", e.getId(), e.getEventCode(), null, s);
        g.edge(peer, key, "RECORDED_EVENT", "PACKAGE_TASK_EVENT", e.getId(), s).setSourceVersion(e.getSourceVersion());
        if (taskKey != null && g.nodes.containsKey(taskKey)) {
            g.edge(taskKey, key, "HISTORICAL_TASK_EVENT", "PACKAGE_TASK_EVENT", e.getId(), s).setSourceVersion(e.getSourceVersion());
        }
        if (hasText(e.getRelatedPackageCode())) {
            String related = g.packageCodeNode(e.getRelatedPackageCode(), s);
            g.edge(key, related, "HISTORICAL_RELATED_PACKAGE", "PACKAGE_TASK_EVENT", e.getId(), s).setSourceVersion(e.getSourceVersion());
        }
        if (key != null) g.events.put(e.getId(), e);
    }

    private void outbound(Graph g) {
        Section s = g.sections.get("OUTBOUND");
        if (!g.enabled(s)) return;
        for (var line : many(outboundLineMapper, query(RdOutboundOrderLineDO.class, RdOutboundOrderLineDO::getId, g.tenantId)
                .eq(RdOutboundOrderLineDO::getPackageId, g.root.getId()), g, s)) {
            if (g.matchesRoot(line.getPackageId(), line.getPackageCode(), s)) outboundLine(g, line, s);
        }
        for (var loading : g.loadingLines.values()) {
            if (loading.getOutboundOrderLineId() == null || !g.enabled(s)) continue;
            var line = one(outboundLineMapper, RdOutboundOrderLineDO.class, RdOutboundOrderLineDO::getId,
                    loading.getOutboundOrderLineId(), g.tenantId);
            if (line == null || !Objects.equals(line.getPackageId(), loading.getPackageId())
                    || !Objects.equals(line.getOrderId(), loading.getOutboundOrderId())
                    || !Objects.equals(line.getStockTransactionId(), loading.getStockTransactionId())) { incomplete(s); continue; }
            String key = outboundLine(g, line, s);
            g.edge(g.key("LOADING_TASK", loading.getTaskId()), key, "LOADING_OUTBOUND_LINE", "LOADING_TASK_LINE", loading.getId(), s)
                    .setLineStatus(loading.getLineStatus()).setSourceVersion(loading.getVersion());
        }
    }

    private String outboundLine(Graph g, RdOutboundOrderLineDO line, Section s) {
        String existing = g.key("OUTBOUND_LINE", line.getId());
        if (g.outboundLines.containsKey(line.getId())) return existing;
        String peer = g.packageNode(line.getPackageId(), line.getPackageCode(), s);
        if (peer == null) return null;
        String key = g.node("OUTBOUND_LINE", line.getId(), "Outbound line " + line.getId(), null, s);
        g.edge(key, peer, "OUTBOUND_PACKAGE", "OUTBOUND_ORDER_LINE", line.getId(), s);
        var order = one(outboundMapper, RdOutboundOrderDO.class, RdOutboundOrderDO::getId, line.getOrderId(), g.tenantId);
        if (order == null) incomplete(s);
        else {
            String orderKey = g.node("OUTBOUND_ORDER", order.getId(), order.getOutboundNo(), order.getStatus(), s);
            g.edge(key, orderKey, "OUTBOUND_ORDER", "OUTBOUND_ORDER_LINE", line.getId(), s).setSourceVersion(order.getVersion());
        }
        if (key != null) g.outboundLines.put(line.getId(), line);
        return key;
    }

    private void stock(Graph g) {
        Section s = g.sections.get("STOCK_TRANSACTION");
        if (!g.enabled(s)) return;
        for (var transaction : many(transactionMapper, query(RdStockTransactionDO.class, RdStockTransactionDO::getId, g.tenantId)
                .eq(RdStockTransactionDO::getPackageId, g.root.getId()), g, s)) {
            if (Objects.equals(transaction.getPackageId(), g.root.getId())) transaction(g, transaction, s);
            else incomplete(s);
        }
        // These are actual line FKs. relatedOrderId is a sales/inbound ID, never a task/outbound ID.
        for (var line : g.loadingLines.values()) {
            String key = transactionById(g, line.getStockTransactionId(), line.getPackageId(), s);
            g.edge(g.key("LOADING_TASK", line.getTaskId()), key, "LOADING_TRANSACTION", "LOADING_TASK_LINE", line.getId(), s)
                    .setLineStatus(line.getLineStatus()).setSourceVersion(line.getVersion());
        }
        for (var line : g.putawayLines.values()) {
            String key = transactionById(g, line.getStockTransactionId(), line.getPackageId(), s);
            g.edge(g.key("PUTAWAY_TASK", line.getTaskId()), key, "PUTAWAY_TRANSACTION", "PUTAWAY_TASK_LINE", line.getId(), s)
                    .setLineStatus(line.getLineStatus()).setSourceVersion(line.getVersion());
        }
        for (var line : g.outboundLines.values()) {
            String key = transactionById(g, line.getStockTransactionId(), line.getPackageId(), s);
            g.edge(g.key("OUTBOUND_LINE", line.getId()), key, "OUTBOUND_TRANSACTION", "OUTBOUND_ORDER_LINE", line.getId(), s);
        }
    }

    private String transactionById(Graph g, Long id, Long packageId, Section s) {
        if (id == null || !g.enabled(s)) return null;
        var transaction = g.transactions.get(id);
        if (transaction == null) transaction = one(transactionMapper, RdStockTransactionDO.class, RdStockTransactionDO::getId, id, g.tenantId);
        if (transaction == null || !Objects.equals(transaction.getPackageId(), packageId)) { incomplete(s); return null; }
        return transaction(g, transaction, s);
    }

    private String transaction(Graph g, RdStockTransactionDO transaction, Section s) {
        String peer = g.packageNode(transaction.getPackageId(), null, s);
        if (peer == null) return null;
        String key = g.node("STOCK_TRANSACTION", transaction.getId(), "Stock transaction " + transaction.getId(), transaction.getTransactionType(), s);
        g.edge(peer, key, "RECORDED_TRANSACTION", "STOCK_TRANSACTION", transaction.getId(), s);
        if (key != null) g.transactions.put(transaction.getId(), transaction);
        return key;
    }

    private void positions(Graph g) {
        Section s = g.sections.get("POSITION");
        if (!g.enabled(s)) return;
        for (var unit : g.packages.values()) {
            boolean inStock = Set.of("IN_STOCK", "OUTBOUND_PENDING").contains(Objects.toString(unit.getStatus(), ""));
            if (inStock && unit.getLocationId() == null) incomplete(s);
            String relation = inStock && unit.getLocationId() != null ? "CURRENT_POSITION" : "LAST_RECORDED_POSITION";
            String key = location(g, unit.getLocationId(), unit.getLocationCode(), "PACKAGE_UNIT", unit.getId(), relation, s);
            g.edge(g.key("PACKAGE", unit.getId()), key, relation, "PACKAGE_UNIT", unit.getId(), s).setSourceVersion(unit.getVersion());
        }
        for (var task : g.putawayTasks.values()) {
            String planned = location(g, task.getPlannedLocationId(), task.getPlannedLocationCode(), "PUTAWAY_TASK", task.getId(), "PLANNED", s);
            g.edge(g.key("PUTAWAY_TASK", task.getId()), planned, "PLANNED_POSITION", "PUTAWAY_TASK", task.getId(), s).setSourceVersion(task.getVersion());
            String recorded = location(g, task.getFinalLocationId(), task.getFinalLocationCode(), "PUTAWAY_TASK", task.getId(), "FINAL", s);
            g.edge(g.key("PUTAWAY_TASK", task.getId()), recorded, "RECORDED_FINAL_POSITION", "PUTAWAY_TASK", task.getId(), s).setSourceVersion(task.getVersion());
        }
        for (var line : g.loadingLines.values()) {
            String planned = location(g, line.getPlannedLocationId(), line.getPlannedLocationCode(), "LOADING_TASK_LINE", line.getId(), "PLANNED", s);
            g.edge(g.key("LOADING_TASK", line.getTaskId()), planned, "PLANNED_POSITION", "LOADING_TASK_LINE", line.getId(), s)
                    .setLineStatus(line.getLineStatus()).setSourceVersion(line.getVersion());
        }
        for (var e : g.events.values()) {
            // Task-event locationCode can be a planned location; it is not proof of occupancy.
            String key = g.snapshot("PACKAGE_TASK_EVENT", e.getId(), "RECORDED", e.getLocationCode(), s);
            g.edge(g.key("TASK_EVENT", e.getId()), key, "EVENT_RECORDED_POSITION", "PACKAGE_TASK_EVENT", e.getId(), s).setSourceVersion(e.getSourceVersion());
            String previous = g.snapshot("PACKAGE_TASK_EVENT", e.getId(), "PREVIOUS", e.getPreviousLocationCode(), s);
            g.edge(g.key("TASK_EVENT", e.getId()), previous, "HISTORICAL_PREVIOUS_POSITION", "PACKAGE_TASK_EVENT", e.getId(), s).setSourceVersion(e.getSourceVersion());
        }
        for (var t : g.transactions.values()) {
            String key = g.snapshot("STOCK_TRANSACTION", t.getId(), "RECORDED", t.getLocationCode(), s);
            g.edge(g.key("STOCK_TRANSACTION", t.getId()), key, "TRANSACTION_RECORDED_POSITION", "STOCK_TRANSACTION", t.getId(), s);
        }
    }

    private String location(Graph g, Long id, String code, String evidenceType, Long evidenceId, String role, Section s) {
        if (id == null) return g.snapshot(evidenceType, evidenceId, role, code, s);
        if (!g.enabled(s)) return null;
        var location = one(locationMapper, RdWarehouseLocationDO.class, RdWarehouseLocationDO::getId, id, g.tenantId);
        if (location == null || hasText(code) && !Objects.equals(code, location.getLocationCode())) { incomplete(s); return null; }
        String key = g.node("LOCATION", id, location.getLocationCode(), null, s);
        if (key != null) g.nodes.get(key).setLocationId(id).setLocationCode(location.getLocationCode());
        return key;
    }

    private static <T extends TenantBaseDO> LambdaQueryWrapperX<T> query(Class<T> type, SFunction<T, Long> idColumn, Long tenantId) {
        var query = new LambdaQueryWrapperX<T>();
        query.setEntityClass(type);
        query.eq(TenantBaseDO::getTenantId, tenantId).eq(TenantBaseDO::getDeleted, false);
        query.orderByAsc(idColumn);
        return query.last("LIMIT 101");
    }

    private static <T extends TenantBaseDO> T one(BaseMapperX<T> mapper, Class<T> type, SFunction<T, Long> idColumn, Long id, Long tenantId) {
        if (id == null) return null;
        return mapper.selectList(query(type, idColumn, tenantId).eq(idColumn, id).last("LIMIT 1")).stream()
                .filter(row -> visible(row, tenantId) && Objects.equals(idColumn.apply(row), id)).findFirst().orElse(null);
    }

    private static <T extends TenantBaseDO> List<T> many(BaseMapperX<T> mapper, LambdaQueryWrapperX<T> query, Graph g, Section s) {
        List<T> rows = mapper.selectList(query);
        if (rows.size() > ROW_LIMIT) limited(s, "ROW_LIMIT_100");
        List<T> result = new ArrayList<>();
        for (T row : rows.subList(0, Math.min(rows.size(), ROW_LIMIT))) {
            if (visible(row, g.tenantId)) result.add(row);
            else incomplete(s);
        }
        return result;
    }

    private static boolean visible(TenantBaseDO row, Long tenantId) {
        return row != null && Objects.equals(row.getTenantId(), tenantId) && Boolean.FALSE.equals(row.getDeleted());
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }

    private static void incomplete(Section s) {
        if ("COMPLETE".equals(s.getState())) s.setState("INCOMPLETE").setReason("MISSING_OR_INVALID_REFERENCE");
    }

    private static void limited(Section s, String reason) {
        if ("REDACTED".equals(s.getState())) return;
        if (!"LIMITED".equals(s.getState())) s.setState("LIMITED").setReason(reason);
        else if (!s.getReason().contains(reason)) s.setReason(s.getReason() + ";" + reason);
    }

    private class Graph {
        private final Long tenantId;
        private final RdPackageUnitDO root;
        private final String rootKey;
        private final Map<String, Node> nodes = new LinkedHashMap<>();
        private final Map<String, Edge> edges = new LinkedHashMap<>();
        private final Map<String, Section> sections = new LinkedHashMap<>();
        private final Map<Long, RdPackageUnitDO> packages = new LinkedHashMap<>();
        private final Map<Long, RdPutawayTaskDO> putawayTasks = new LinkedHashMap<>();
        private final Map<Long, RdPutawayTaskLineDO> putawayLines = new LinkedHashMap<>();
        private final Map<Long, RdLoadingTaskLineDO> loadingLines = new LinkedHashMap<>();
        private final Map<Long, RdPackageTaskEventDO> events = new LinkedHashMap<>();
        private final Map<Long, RdOutboundOrderLineDO> outboundLines = new LinkedHashMap<>();
        private final Map<Long, RdStockTransactionDO> transactions = new LinkedHashMap<>();

        private Graph(Long tenantId, RdPackageUnitDO root) {
            this.tenantId = tenantId;
            this.root = root;
            rootKey = key("PACKAGE", root.getId());
            nodes.put(rootKey, new Node().setNodeKey(rootKey).setKind("PACKAGE").setLabel(root.getPackageCode())
                    .setReferenceId(root.getId()).setPackageCode(root.getPackageCode()).setStatus(root.getStatus()));
            packages.put(root.getId(), root);
        }

        private String key(String kind, Long id) { return id == null ? null : kind + ":" + tenantId + ":" + id; }

        private void authorize(String kind, String permission, Long userId) {
            boolean allowed = permissionApi.hasAnyPermissions(userId, permission);
            sections.put(kind, new Section().setKind(kind).setState(allowed ? "COMPLETE" : "REDACTED")
                    .setReason(allowed ? "NO_PERSISTED_RELATIONS" : "QUERY_PERMISSION_REQUIRED"));
        }

        private boolean enabled(Section s) {
            if ("REDACTED".equals(s.getState())) return false;
            if (nodes.size() >= NODE_LIMIT || edges.size() >= EDGE_LIMIT) { limit(s); return false; }
            return true;
        }

        private void limit(Section s) {
            limited(s, GLOBAL_LIMIT);
            sections.computeIfAbsent("GRAPH", k -> new Section().setKind(k).setState("COMPLETE").setReason("ROOT_GROUPS"));
            limited(sections.get("GRAPH"), GLOBAL_LIMIT);
        }

        private String node(String kind, Long id, String label, String status, Section s) {
            if (id == null) { incomplete(s); return null; }
            String key = key(kind, id);
            if (nodes.containsKey(key)) return key;
            if (!enabled(s)) return null;
            nodes.put(key, new Node().setNodeKey(key).setKind(kind).setReferenceId(id)
                    .setLabel(hasText(label) ? label : kind + " " + id).setStatus(status));
            return key;
        }

        private String packageNode(Long id, String code, Section s) {
            if (id == null) { incomplete(s); return null; }
            RdPackageUnitDO unit = packages.get(id);
            if (unit == null) {
                if (!enabled(s)) return null;
                unit = one(packageMapper, RdPackageUnitDO.class, RdPackageUnitDO::getId, id, tenantId);
            }
            if (unit == null || !hasText(unit.getPackageCode()) || code != null && !Objects.equals(unit.getPackageCode(), code)) {
                incomplete(s); return null;
            }
            String key = node("PACKAGE", id, unit.getPackageCode(), unit.getStatus(), s);
            if (key != null) {
                nodes.get(key).setPackageCode(unit.getPackageCode());
                packages.put(id, unit);
            }
            return key;
        }

        private String packageCodeNode(String code, Section s) {
            var existing = packages.values().stream().filter(p -> code.equals(p.getPackageCode())).findFirst().orElse(null);
            if (existing != null) return key("PACKAGE", existing.getId());
            if (!enabled(s)) return null;
            var found = packageMapper.selectList(query(RdPackageUnitDO.class, RdPackageUnitDO::getId, tenantId)
                    .eq(RdPackageUnitDO::getPackageCode, code).last("LIMIT 1")).stream()
                    .filter(p -> visible(p, tenantId) && code.equals(p.getPackageCode())).findFirst().orElse(null);
            if (found == null) { incomplete(s); return null; }
            return packageNode(found.getId(), code, s);
        }

        private boolean matchesRoot(Long id, String code, Section s) {
            boolean matches = Objects.equals(root.getId(), id) && Objects.equals(root.getPackageCode(), code);
            if (!matches) incomplete(s);
            return matches;
        }

        private String snapshot(String evidenceType, Long evidenceId, String role, String code, Section s) {
            if (!hasText(code)) return null;
            String key = "LOCATION_SNAPSHOT:" + tenantId + ":" + evidenceType + ":" + evidenceId + ":" + role;
            if (!nodes.containsKey(key)) {
                if (!enabled(s)) return null;
                nodes.put(key, new Node().setNodeKey(key).setKind("LOCATION_SNAPSHOT").setLabel(code)
                        .setLocationCode(code).setDescription("Recorded position snapshot; not current occupancy"));
            }
            return key;
        }

        private Edge edge(String from, String to, String relation, String evidenceType, Long evidenceId, Section s) {
            String key = relation + ":" + evidenceType + ":" + tenantId + ":" + evidenceId + ":" + from + ":" + to;
            Edge edge = edges.get(key);
            if (edge != null) return edge;
            edge = new Edge().setEdgeKey(key).setFromNodeKey(from).setToNodeKey(to).setRelation(relation)
                    .setLabel(switch (relation) {
                        case "CURRENT_POSITION" -> "Current position";
                        case "LAST_RECORDED_POSITION" -> "Last recorded position";
                        case "PLANNED_POSITION" -> "Planned position";
                        case "RECORDED_FINAL_POSITION" -> "Recorded final position";
                        case "EVENT_RECORDED_POSITION" -> "Recorded event position (not occupancy)";
                        case "HISTORICAL_PREVIOUS_POSITION" -> "Historical previous position";
                        case "TRANSACTION_RECORDED_POSITION" -> "Recorded transaction position";
                        default -> relation;
                    }).setEvidenceType(evidenceType).setEvidenceId(evidenceId);
            if (from == null || to == null || !nodes.containsKey(from) || !nodes.containsKey(to)) return edge;
            if (edges.size() >= EDGE_LIMIT) { limit(s); return edge; }
            edges.put(key, edge);
            s.setReturnedCount(s.getReturnedCount() + 1);
            return edge;
        }

        private RdPackageRelationsRespVO response() {
            Section graph = sections.computeIfAbsent("GRAPH", k -> new Section().setKind(k).setState("COMPLETE").setReason("ROOT_GROUPS"));
            graph.setReturnedCount(nodes.size());
            sections.values().stream().filter(s -> "COMPLETE".equals(s.getState()) && s.getReturnedCount() > 0 && !"GRAPH".equals(s.getKind()))
                    .forEach(s -> s.setReason("ROOT_PERSISTED_RELATIONS"));
            return new RdPackageRelationsRespVO().setRootPackageCode(root.getPackageCode()).setRootNodeKey(rootKey).setScope("ROOT_GROUPS")
                    .setNodes(List.copyOf(nodes.values())).setEdges(List.copyOf(edges.values())).setSections(List.copyOf(sections.values()));
        }
    }
}
