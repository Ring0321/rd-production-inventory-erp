package com.ruiding.erp.service.weight;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import com.ruiding.erp.controller.admin.weight.vo.RdWeightSuggestReqVO;
import com.ruiding.erp.controller.admin.weight.vo.RdWeightSuggestRespVO;
import com.ruiding.erp.dal.dataobject.loading.RdPickupSlipLineDO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Semaphore;

@Service
public class RdWeightAssistantService {
    @Resource private PermissionService permissionService;
    @Resource private RdWeightInventoryReader reader;
    @Resource private RdWeightSolver solver;
    private final Semaphore slots = new Semaphore(2);

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 10)
    public RdWeightSuggestRespVO suggest(RdWeightSuggestReqVO req, Long userId) {
        requireAccess(userId);
        validateRequest(req);
        long started = System.nanoTime();
        String factsAt = Instant.now().toString();
        var slip = reader.readSlip(req.getPickupSlipId());
        Long tenantId = TenantContextHolder.getTenantId();
        if (slip == null || !Objects.equals(slip.getTenantId(), tenantId) ||
                !Objects.equals(slip.getId(), req.getPickupSlipId()) || !"ISSUED".equals(slip.getStatus()) ||
                slip.getCustomerName() == null || slip.getCustomerName().isBlank())
            throw invalid("提货单不存在或不可用于配吨");
        List<RdPickupSlipLineDO> lines = reader.readLines(slip.getId());
        if (lines.isEmpty() || lines.stream().anyMatch(item -> !Objects.equals(item.getTenantId(), tenantId) ||
                !Objects.equals(item.getSlipId(), slip.getId()))) throw invalid("提货行归属未核实");
        Long lineId = req.getPickupSlipLineId();
        if (lineId == null && lines.size() == 1) lineId = lines.get(0).getId();
        Long selectedId = lineId;
        var line = lines.stream().filter(item -> Objects.equals(item.getId(), selectedId)).findFirst()
                .orElseThrow(() -> invalid("请选择本提货单的明确提货行"));
        long authorized;
        try { authorized = RdWeightSolver.grams(line.getRequestedWeight()); }
        catch (IllegalArgumentException ex) { throw invalid("本提货行缺少有效授权重量或精度不受支持"); }
        long upper = RdWeightSolver.grams(req.getTargetWeightKg()) + RdWeightSolver.grams(req.getToleranceKg());
        if (authorized <= 0 || upper > authorized || line.getId() == null || line.getId() <= 0 ||
                line.getProductModel() == null || line.getProductModel().isBlank() ||
                line.getRequestedPackageCount() != null && line.getRequestedPackageCount() <= 0 ||
                slip.getRequestedPackageCount() != null && slip.getRequestedPackageCount() <= 0 ||
                slip.getRequestedWeight() != null && req.getTargetWeightKg().add(req.getToleranceKg()).compareTo(slip.getRequestedWeight()) > 0)
            throw invalid("目标加容差不得超过原提货行及提货单授权");
        if (reader.hasTask(slip.getId())) throw invalid("该提货单已有整车任务，请在原任务中核对，不拆分配吨");
        var response = new RdWeightSuggestRespVO().setPickupSlipId(slip.getId()).setPickupSlipLineId(line.getId())
                .setSlipNo(slip.getSlipNo()).setCustomerName(slip.getCustomerName()).setProductModel(line.getProductModel())
                .setAuthorizedWeightKg(line.getRequestedWeight()).setAuthorizedPackageCount(line.getRequestedPackageCount())
                .setTargetWeightKg(req.getTargetWeightKg()).setToleranceKg(req.getToleranceKg()).setFactsAt(factsAt);
        if (!slots.tryAcquire()) return response.setSolverStatus("BUSY").setMessage("计算并发预算已用尽，请稍后重试");
        try {
            var inventory = reader.readCandidates(slip, line);
            response.setScannedCount(inventory.scannedCount()).setCandidateScanComplete(inventory.complete())
                    .setCandidateCount(inventory.complete() ? inventory.bags().size() : null);
            if (!inventory.complete()) return response.setSolverStatus("BUDGET_EXCEEDED")
                    .setMessage("候选扫描超过500包或读取预算，未截断求解；请核对原提货行范围后重试");
            int maxCount = Math.min(500, line.getRequestedPackageCount() == null ? 500 : line.getRequestedPackageCount());
            if (slip.getRequestedPackageCount() != null) maxCount = Math.min(maxCount, slip.getRequestedPackageCount());
            BigDecimal upperBound = availableUpperBound(inventory, maxCount);
            response.setAvailableWeightUpperBoundKg(upperBound);
            var result = solver.solve(inventory.bags(), inventory.lotCaps(), maxCount,
                    req.getTargetWeightKg(), req.getToleranceKg(), 2);
            response.setSolverStatus(result.status()).setBags(result.bags()).setTotalWeightKg(result.totalWeightKg())
                    .setObjectiveBound(result.objectiveBound()).setMessage(message(result.status()));
            if (result.totalWeightKg() != null && ("OPTIMAL".equals(result.status()) || "FEASIBLE".equals(result.status()))) {
                response.setDeviationKg(result.totalWeightKg().subtract(req.getTargetWeightKg()))
                        .setShortfallKg(req.getTargetWeightKg().subtract(result.totalWeightKg()).max(BigDecimal.ZERO))
                        .setWithinTolerance(result.totalWeightKg().subtract(req.getTargetWeightKg()).abs().compareTo(req.getToleranceKg()) <= 0);
            } else if (upperBound.compareTo(req.getTargetWeightKg()) < 0) {
                response.setShortfallKg(req.getTargetWeightKg().subtract(upperBound));
            }
            return response;
        } catch (LinkageError ex) {
            return response.setSolverStatus("UNAVAILABLE").setMessage(message("UNAVAILABLE"));
        } finally {
            slots.release();
            response.setElapsedMs((System.nanoTime() - started) / 1_000_000);
        }
    }

    private void requireAccess(Long userId) {
        if (userId == null || userId <= 0 || TenantContextHolder.getTenantId() == null ||
                permissionService.hasAnyRoles(userId, "rd_forklift_driver"))
            throw new ServiceException(403, "当前身份无配吨权限");
        for (String permission : List.of("rd:pickup-slip:query", "rd:package-unit:query", "rd:loading-task:dispatch"))
            if (!permissionService.hasAnyPermissions(userId, permission)) throw new ServiceException(403, "当前身份无配吨权限");
    }

    private static void validateRequest(RdWeightSuggestReqVO req) {
        if (req == null || req.getPickupSlipId() == null || req.getPickupSlipId() <= 0 ||
                req.getPickupSlipLineId() != null && req.getPickupSlipLineId() <= 0) throw invalid("请选择原提货单及提货行");
        try {
            long target = RdWeightSolver.grams(req.getTargetWeightKg());
            long tolerance = RdWeightSolver.grams(req.getToleranceKg());
            if (target <= 0 || tolerance >= target) throw invalid("目标应大于零且容差应小于目标");
        } catch (IllegalArgumentException ex) { throw invalid("重量范围为0至1000000 kg，精度为0.001 kg，不做四舍五入"); }
    }

    private static BigDecimal availableUpperBound(RdWeightInventoryReader.Inventory inventory, int maxCount) {
        Map<Long, Integer> used = new HashMap<>();
        long total = 0;
        int count = 0;
        for (var bag : inventory.bags().stream().sorted(Comparator.comparing(RdWeightSolver.Bag::packageWeightKg).reversed()).toList()) {
            if (count == maxCount) break;
            if (used.getOrDefault(bag.stockLotId(), 0) >= inventory.lotCaps().getOrDefault(bag.stockLotId(), 0)) continue;
            used.merge(bag.stockLotId(), 1, Integer::sum);
            total += RdWeightSolver.grams(bag.packageWeightKg());
            count++;
        }
        return BigDecimal.valueOf(total, 3);
    }

    private static String message(String status) {
        return switch (status) {
            case "OPTIMAL" -> "在本次授权候选与预算内已证明最小重量偏差，其次最少包数；未锁货，原整车任务仍须仓管核对";
            case "FEASIBLE" -> "预算内得到容差内建议，未证明最优；未锁货，原整车任务仍须仓管核对";
            case "INFEASIBLE" -> "当前授权候选及包数/占用约束下，容差内无可行组合；未改变原任务";
            case "UNKNOWN" -> "求解预算已结束，未找到可行组合且未证明无解";
            case "UNAVAILABLE" -> "OR-Tools原生求解器或Windows C++运行库不可用，未执行替代或模拟求解";
            default -> "求解模型未通过校验，未生成配吨建议";
        };
    }

    private static ServiceException invalid(String message) { return new ServiceException(400, message); }
}
