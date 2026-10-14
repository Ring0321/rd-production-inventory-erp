package com.ruiding.erp.service.loading;

import com.ruiding.erp.controller.admin.loading.vo.*;
import com.ruiding.erp.dal.mysql.loading.*;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.ruiding.erp.dal.dataobject.loading.*;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.ruiding.erp.enums.ErrorCodeConstants.*;

@Service
public class RdLoadingReductionServiceImpl implements RdLoadingReductionService {
    @Resource private RdLoadingReductionMapper reductionMapper;
    @Resource private RdLoadingTaskMapper taskMapper;
    @Resource private RdLoadingTaskLineMapper lineMapper;
    @Resource private RdPickupSlipMapper slipMapper;
    @Resource private RdPickupSlipLineMapper slipLineMapper;
    @Resource private RdLoadingPackageClaimMapper claimMapper;
    @Resource private RdLoadingExceptionMapper exceptionMapper;
    @Resource private RdPackageUnitMapper packageMapper;
    @Resource private PermissionService permissionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RdLoadingReductionRespVO create(RdLoadingReductionCreateReqVO req, Long actorId, String actorName) {
        authorize(actorId, "rd:loading-task:dispatch");
        if (!permissionService.hasAnyRoles(actorId, "rd_warehouse", "super_admin")) throw exception(LOADING_REDUCTION_FORBIDDEN);
        if (req == null || req.getTaskId() == null || req.getTaskVersion() == null
                || req.getTaskLineIds() == null || req.getTaskLineIds().isEmpty() || req.getTaskLineIds().size() > 100
                || req.getTaskLineIds().stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(req.getTaskLineIds()).size() != req.getTaskLineIds().size()
                || blank(req.getReason()) || req.getReason().trim().length() > 1000
                || req.getIdempotencyKey() == null || !req.getIdempotencyKey().matches("[A-Za-z0-9_-]{16,64}")) {
            throw exception(LOADING_REDUCTION_INVALID);
        }
        Long tenantId = tenant();
        RdLoadingTaskDO task = requireTask(taskMapper.selectByIdForUpdate(req.getTaskId()), tenantId);
        String requestDigest = digest(Arrays.asList(req.getTaskId(), req.getTaskVersion(), actorId,
                req.getTaskLineIds().stream().sorted().collect(Collectors.toList()), req.getReason().trim()));
        RdLoadingReductionDO prior = reductionMapper.selectByKeyForUpdate(task.getId(), tenantId, req.getIdempotencyKey());
        if (prior != null) {
            if (!Objects.equals(prior.getProposerId(), actorId) || !Objects.equals(prior.getRequestDigest(), requestDigest)) {
                throw exception(LOADING_REDUCTION_KEY_CONFLICT);
            }
            return response(prior);
        }
        requireExceptionTask(task, req.getTaskVersion());
        List<RdLoadingTaskLineDO> lines = lineMapper.selectListByTaskIdForUpdate(task.getId());
        Set<Long> removed = new HashSet<>(req.getTaskLineIds());
        if (lines.size() <= removed.size() || !lines.stream().map(RdLoadingTaskLineDO::getId)
                .collect(Collectors.toSet()).containsAll(removed)) throw exception(LOADING_REDUCTION_INVALID);
        for (RdLoadingTaskLineDO line : lines) {
            if (!Objects.equals(line.getTaskId(), task.getId()) || !Objects.equals(line.getTenantId(), tenantId)
                    || !Set.of("PLANNED", "EXCEPTION", "SCANNED").contains(line.getLineStatus())
                    || line.getOutboundOrderId() != null || line.getStockTransactionId() != null
                    || line.getPackageWeight() == null || line.getPackageWeight().signum() <= 0
                    || removed.contains(line.getId()) && ("SCANNED".equals(line.getLineStatus()) || line.getScanTime() != null)) {
                throw exception(LOADING_REDUCTION_INVALID);
            }
        }
        RdPickupSlipDO slip = requireSlip(task, tenantId);
        validatePickupPlan(slip, lines, tenantId);
        List<RdLoadingReductionRespVO.Line> snapshot = validatePackages(lines, task, tenantId);
        BigDecimal originalWeight = sum(snapshot);
        if (!Objects.equals(task.getPlannedPackageCount(), lines.size()) || !same(task.getPlannedWeight(), originalWeight)
                || slip.getRequestedPackageCount() != null && !Objects.equals(slip.getRequestedPackageCount(), lines.size())
                || slip.getRequestedWeight() != null && !same(slip.getRequestedWeight(), originalWeight)) {
            throw exception(LOADING_REDUCTION_STALE);
        }
        for (RdLoadingReductionDO item : reductionMapper.selectTaskListForUpdate(task.getId(), tenantId)) {
            if (Set.of("PENDING", "APPROVED").contains(item.getStatus()) && current(item, task, slip, lines)
                    && physicalSnapshotCurrent(item, JsonUtils.parseArray(item.getPlanLinesJson(), RdLoadingReductionRespVO.Line.class))) {
                throw exception(LOADING_REDUCTION_ACTIVE);
            }
        }
        RdLoadingReductionDO item = new RdLoadingReductionDO();
        item.setTenantId(tenantId); item.setTaskId(task.getId()); item.setPickupSlipId(slip.getId());
        item.setTaskNo(task.getTaskNo()); item.setPickupSlipNo(task.getPickupSlipNo());
        item.setCustomerName(task.getCustomerName()); item.setStatus("PENDING");
        item.setTaskVersion(task.getVersion()); item.setPlanVersion(task.getPlanVersion()); item.setSlipVersion(slip.getVersion());
        item.setVersion(0); item.setIdempotencyKey(req.getIdempotencyKey()); item.setRequestDigest(requestDigest);
        item.setPlanDigest(digest(snapshot(lines))); item.setSlipDigest(slipDigest(slip));
        snapshot.forEach(line -> line.setRemoved(removed.contains(line.getId())));
        item.setPlanLinesJson(JsonUtils.toJsonString(snapshot));
        item.setOriginalPackageCount(lines.size()); item.setReducedPackageCount(removed.size());
        item.setRemainingPackageCount(lines.size() - removed.size()); item.setOriginalWeight(originalWeight);
        item.setReducedWeight(snapshot.stream().filter(RdLoadingReductionRespVO.Line::isRemoved)
                .map(RdLoadingReductionRespVO.Line::getPackageWeight).reduce(BigDecimal.ZERO, BigDecimal::add));
        item.setRemainingWeight(originalWeight.subtract(item.getReducedWeight()));
        item.setReason(req.getReason().trim()); item.setProposerId(actorId); item.setProposerName(actorName);
        item.setProposedTime(LocalDateTime.now());
        reductionMapper.insert(item);
        return response(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RdLoadingReductionRespVO review(RdLoadingReductionReviewReqVO req, Long actorId, String actorName) {
        authorize(actorId, "rd:pickup-slip:approve-reduction");
        if (!permissionService.hasAnyRoles(actorId, "rd_sales", "super_admin")) throw exception(LOADING_REDUCTION_FORBIDDEN);
        if (req == null || req.getId() == null || req.getVersion() == null
                || req.getDecision() == null || !Set.of("APPROVED", "REJECTED").contains(req.getDecision())
                || blank(req.getReviewRemark()) || req.getReviewRemark().trim().length() > 1000) {
            throw exception(LOADING_REDUCTION_INVALID);
        }
        Long tenantId = tenant();
        RdLoadingReductionDO lookup = reductionMapper.selectById(req.getId());
        if (lookup == null || !Objects.equals(lookup.getTenantId(), tenantId)) throw exception(LOADING_REDUCTION_NOT_EXISTS);
        // Every writer locks task first; commercial review never locks stock before the task.
        RdLoadingTaskDO task = requireTask(taskMapper.selectByIdForUpdate(lookup.getTaskId()), tenantId);
        RdLoadingReductionDO item = reductionMapper.selectByIdForUpdate(req.getId(), tenantId);
        if (item == null || !Objects.equals(item.getTaskId(), task.getId())) throw exception(LOADING_REDUCTION_NOT_EXISTS);
        if (Objects.equals(item.getProposerId(), actorId)) throw exception(LOADING_REDUCTION_SELF_REVIEW);
        if (!"PENDING".equals(item.getStatus())) {
            if (Objects.equals(item.getStatus(), req.getDecision()) && Objects.equals(item.getReviewerId(), actorId)
                    && Objects.equals(item.getReviewRemark(), req.getReviewRemark().trim())
                    && Objects.equals(item.getVersion(), req.getVersion() + 1)) return response(item);
            throw exception(LOADING_REDUCTION_REVIEW_CONFLICT);
        }
        if (!Objects.equals(item.getVersion(), req.getVersion())) throw exception(LOADING_REDUCTION_REVIEW_CONFLICT);
        if ("APPROVED".equals(req.getDecision())) {
            requireExceptionTask(task, item.getTaskVersion());
            RdPickupSlipDO slip = requireSlip(task, tenantId);
            List<RdLoadingTaskLineDO> lines = lineMapper.selectListByTaskIdForUpdate(task.getId());
            if (!current(item, task, slip, lines)) throw exception(LOADING_REDUCTION_STALE);
            validatePickupPlan(slip, lines, tenantId);
            List<RdLoadingReductionRespVO.Line> expected = JsonUtils.parseArray(item.getPlanLinesJson(), RdLoadingReductionRespVO.Line.class);
            expected.forEach(line -> line.setRemoved(false));
            if (!Objects.equals(digest(expected), digest(validatePackages(lines, task, tenantId)))) throw exception(LOADING_REDUCTION_STALE);
        }
        item.setStatus(req.getDecision()); item.setReviewerId(actorId); item.setReviewerName(actorName);
        item.setReviewRemark(req.getReviewRemark().trim()); item.setReviewTime(LocalDateTime.now());
        item.setVersion(item.getVersion() + 1); reductionMapper.updateById(item);
        return response(item);
    }

    @Override
    public List<RdLoadingReductionRespVO> list(Long taskId, Long pickupSlipId, Long actorId) {
        authorize(actorId, "rd:loading-task:query", "rd:pickup-slip:query");
        if ((taskId == null) == (pickupSlipId == null) || taskId != null && taskId <= 0
                || pickupSlipId != null && pickupSlipId <= 0) throw exception(LOADING_REDUCTION_INVALID);
        Long tenantId = tenant();
        List<RdLoadingReductionDO> items;
        if (taskId != null) {
            requireTask(taskMapper.selectById(taskId), tenantId);
            items = reductionMapper.selectTaskList(taskId, tenantId);
        } else {
            RdPickupSlipDO slip = slipMapper.selectById(pickupSlipId);
            if (slip == null || !Objects.equals(slip.getTenantId(), tenantId)) throw exception(PICKUP_SLIP_NOT_EXISTS);
            items = reductionMapper.selectSlipList(pickupSlipId, tenantId);
        }
        return items.stream().map(this::response).collect(Collectors.toList());
    }

    @Override
    public RdLoadingReductionRespVO get(Long id, Long taskId, String idempotencyKey, Long actorId) {
        authorize(actorId, "rd:loading-task:query", "rd:pickup-slip:query");
        boolean byId = id != null && id > 0 && taskId == null && idempotencyKey == null;
        boolean byKey = id == null && taskId != null && taskId > 0 && idempotencyKey != null
                && idempotencyKey.matches("[A-Za-z0-9_-]{16,64}");
        if (!byId && !byKey) throw exception(LOADING_REDUCTION_INVALID);
        Long tenantId = tenant();
        if (byKey) requireTask(taskMapper.selectById(taskId), tenantId);
        RdLoadingReductionDO item = byId ? reductionMapper.selectById(id) : reductionMapper.selectByKey(taskId, tenantId, idempotencyKey);
        return item == null || !Objects.equals(item.getTenantId(), tenantId) ? null : response(item);
    }

    private RdLoadingReductionRespVO response(RdLoadingReductionDO item) {
        RdLoadingReductionRespVO result = new RdLoadingReductionRespVO();
        BeanUtils.copyProperties(item, result);
        result.setLines(JsonUtils.parseArray(item.getPlanLinesJson(), RdLoadingReductionRespVO.Line.class));
        result.setEffectiveStatus(item.getStatus());
        if (Set.of("PENDING", "APPROVED").contains(item.getStatus())) {
            RdLoadingTaskDO task = taskMapper.selectById(item.getTaskId());
            RdPickupSlipDO slip = slipMapper.selectById(item.getPickupSlipId());
            if (!current(item, task, slip, lineMapper.selectListByTaskId(item.getTaskId()))
                    || !physicalSnapshotCurrent(item, result.getLines())) {
                result.setEffectiveStatus("STALE");
            }
        }
        return result;
    }

    private boolean current(RdLoadingReductionDO item, RdLoadingTaskDO task, RdPickupSlipDO slip, List<RdLoadingTaskLineDO> lines) {
        return task != null && slip != null && Objects.equals(task.getTenantId(), item.getTenantId())
                && Objects.equals(slip.getTenantId(), item.getTenantId())
                && Objects.equals(task.getPickupSlipId(), item.getPickupSlipId())
                && Objects.equals(task.getCustomerName(), item.getCustomerName())
                && Objects.equals(slip.getCustomerName(), item.getCustomerName())
                && "EXCEPTION_PENDING".equals(task.getStatus()) && "DISPATCHED".equals(slip.getStatus())
                && Objects.equals(task.getVersion(), item.getTaskVersion())
                && Objects.equals(task.getPlanVersion(), item.getPlanVersion())
                && Objects.equals(task.getPlannedPackageCount(), item.getOriginalPackageCount())
                && same(task.getPlannedWeight(), item.getOriginalWeight())
                && Objects.equals(slip.getVersion(), item.getSlipVersion())
                && Objects.equals(item.getPlanDigest(), digest(snapshot(lines)))
                && Objects.equals(item.getSlipDigest(), slipDigest(slip));
    }

    private void requireExceptionTask(RdLoadingTaskDO task, Integer version) {
        if (!Objects.equals(task.getVersion(), version)) throw exception(LOADING_TASK_VERSION_CONFLICT);
        if (!"EXCEPTION_PENDING".equals(task.getStatus())) throw exception(LOADING_TASK_STATUS_INVALID);
        if (exceptionMapper.selectOpenListByTaskIdForUpdate(task.getId()).isEmpty()) throw exception(LOADING_EXCEPTION_NOT_EXISTS);
    }

    private RdPickupSlipDO requireSlip(RdLoadingTaskDO task, Long tenantId) {
        RdPickupSlipDO slip = slipMapper.selectByIdForUpdate(task.getPickupSlipId(), tenantId);
        if (slip == null || !Objects.equals(slip.getTenantId(), tenantId) || !"DISPATCHED".equals(slip.getStatus())
                || !Objects.equals(slip.getCustomerName(), task.getCustomerName())) throw exception(LOADING_REDUCTION_STALE);
        return slip;
    }

    private List<RdLoadingReductionRespVO.Line> validatePackages(List<RdLoadingTaskLineDO> lines, RdLoadingTaskDO task, Long tenantId) {
        Set<Long> identities = new HashSet<>();
        List<RdLoadingReductionRespVO.Line> result = snapshot(lines);
        for (RdLoadingTaskLineDO line : lines.stream().sorted(Comparator.comparing(RdLoadingTaskLineDO::getPackageId,
                Comparator.nullsFirst(Comparator.naturalOrder()))).collect(Collectors.toList())) {
            if (line.getPackageId() == null || !identities.add(line.getPackageId())) throw exception(LOADING_REDUCTION_STALE);
            RdPackageUnitDO unit = packageMapper.selectByIdForUpdate(line.getPackageId());
            RdLoadingPackageClaimDO claim = claimMapper.selectByPackageIdForUpdate(line.getPackageId());
            var copy = result.stream().filter(item -> Objects.equals(item.getId(), line.getId())).findFirst().orElseThrow();
            if (unit == null || claim == null || unit.getVersion() == null || claim.getId() == null) throw exception(LOADING_REDUCTION_STALE);
            copy.setPackageVersion(unit.getVersion()); copy.setClaimId(claim.getId());
            if (!packageMatches(copy, unit, claim, task.getId(), task.getCustomerName(), tenantId)) throw exception(LOADING_REDUCTION_STALE);
        }
        return result;
    }

    private boolean physicalSnapshotCurrent(RdLoadingReductionDO item, List<RdLoadingReductionRespVO.Line> lines) {
        Map<Long, RdPackageUnitDO> units = packageMapper.selectBatchIds(lines.stream()
                .map(RdLoadingReductionRespVO.Line::getPackageId).collect(Collectors.toList())).stream()
                .collect(Collectors.toMap(RdPackageUnitDO::getId, unit -> unit));
        Map<Long, RdLoadingPackageClaimDO> claims = claimMapper.selectTaskList(item.getTaskId(), item.getTenantId()).stream()
                .collect(Collectors.toMap(RdLoadingPackageClaimDO::getPackageId, claim -> claim));
        return lines.stream().allMatch(line -> packageMatches(line, units.get(line.getPackageId()), claims.get(line.getPackageId()),
                item.getTaskId(), item.getCustomerName(), item.getTenantId()));
    }

    private static boolean packageMatches(RdLoadingReductionRespVO.Line line, RdPackageUnitDO unit, RdLoadingPackageClaimDO claim,
                                          Long taskId, String customer, Long tenantId) {
        return unit != null && claim != null && "IN_STOCK".equals(unit.getStatus())
                && Objects.equals(unit.getTenantId(), tenantId) && Objects.equals(claim.getTenantId(), tenantId)
                && Objects.equals(unit.getVersion(), line.getPackageVersion()) && Objects.equals(claim.getId(), line.getClaimId())
                && Objects.equals(claim.getTaskId(), taskId) && Objects.equals(claim.getTaskLineId(), line.getId())
                && Objects.equals(unit.getPackageCode(), line.getPackageCode())
                && Objects.equals(unit.getCustomerName(), line.getCustomerName()) && Objects.equals(unit.getCustomerName(), customer)
                && Objects.equals(unit.getProductModel(), line.getProductModel()) && Objects.equals(unit.getFurnaceNo(), line.getFurnaceNo())
                && Objects.equals(unit.getFurnaceLayer(), line.getFurnaceLayer()) && same(unit.getPackageWeight(), line.getPackageWeight())
                && unit.getLocationId() != null && Objects.equals(unit.getLocationId(), line.getPlannedLocationId())
                && unit.getStockLotId() != null && Objects.equals(unit.getStockLotId(), line.getStockLotId());
    }

    private void validatePickupPlan(RdPickupSlipDO slip, List<RdLoadingTaskLineDO> lines, Long tenantId) {
        List<RdPickupSlipLineDO> pickup = slipLineMapper.selectListBySlipId(slip.getId());
        Map<Long, RdPickupSlipLineDO> indexed = new HashMap<>();
        for (RdPickupSlipLineDO item : pickup) {
            if (item.getId() == null || !Objects.equals(item.getSlipId(), slip.getId())
                    || !Objects.equals(item.getTenantId(), tenantId) || indexed.put(item.getId(), item) != null) throw exception(LOADING_REDUCTION_STALE);
        }
        if (indexed.isEmpty()) throw exception(LOADING_REDUCTION_STALE);
        Map<Long, List<RdLoadingTaskLineDO>> grouped = new HashMap<>();
        for (RdLoadingTaskLineDO line : lines) {
            RdPickupSlipLineDO item = indexed.get(line.getPickupSlipLineId());
            if (item == null || !Objects.equals(item.getSaleOrderId(), line.getSaleOrderId())
                    || !Objects.equals(item.getProductModel(), line.getProductModel())
                    || !Objects.equals(line.getCustomerName(), slip.getCustomerName())
                    || item.getFurnaceNo() != null && !Objects.equals(item.getFurnaceNo(), line.getFurnaceNo())
                    || item.getFurnaceLayer() != null && !Objects.equals(item.getFurnaceLayer(), line.getFurnaceLayer())) throw exception(LOADING_REDUCTION_STALE);
            grouped.computeIfAbsent(item.getId(), id -> new ArrayList<>()).add(line);
        }
        for (RdPickupSlipLineDO item : pickup) {
            List<RdLoadingTaskLineDO> group = grouped.getOrDefault(item.getId(), List.of());
            BigDecimal weight = group.stream().map(RdLoadingTaskLineDO::getPackageWeight).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (group.isEmpty() || item.getRequestedPackageCount() != null && item.getRequestedPackageCount() != group.size()
                    || item.getRequestedWeight() != null && !same(item.getRequestedWeight(), weight)) throw exception(LOADING_REDUCTION_STALE);
        }
    }

    private String slipDigest(RdPickupSlipDO slip) {
        Map<String, Object> fields = new TreeMap<>();
        fields.put("customer", slip.getCustomerName()); fields.put("plate", slip.getVehiclePlate());
        fields.put("count", slip.getRequestedPackageCount()); fields.put("weight", normalized(slip.getRequestedWeight()));
        fields.put("lines", slipLineMapper.selectListBySlipId(slip.getId()).stream()
                .sorted(Comparator.comparing(RdPickupSlipLineDO::getId)).map(line -> Arrays.asList(line.getId(),
                        line.getSaleOrderId(), line.getProductModel(), line.getFurnaceNo(), line.getFurnaceLayer(),
                        line.getRequestedPackageCount(), normalized(line.getRequestedWeight()))).collect(Collectors.toList()));
        return digest(fields);
    }

    private static List<RdLoadingReductionRespVO.Line> snapshot(List<RdLoadingTaskLineDO> lines) {
        return lines.stream().sorted(Comparator.comparing(RdLoadingTaskLineDO::getId)).map(line -> {
            var copy = new RdLoadingReductionRespVO.Line(); BeanUtils.copyProperties(line, copy);
            copy.setPackageWeight(normalized(line.getPackageWeight())); return copy;
        }).collect(Collectors.toList());
    }
    private static BigDecimal sum(List<RdLoadingReductionRespVO.Line> lines) {
        return lines.stream().map(RdLoadingReductionRespVO.Line::getPackageWeight).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private static BigDecimal normalized(BigDecimal value) { return value == null ? null : new BigDecimal(value.stripTrailingZeros().toPlainString()); }
    private static boolean same(BigDecimal left, BigDecimal right) { return left != null && right != null && left.compareTo(right) == 0; }
    private static String digest(Object value) { return DigestUtil.sha256Hex(JsonUtils.toJsonString(value)); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static Long tenant() {
        Long id = TenantContextHolder.getTenantId();
        if (id == null) throw exception(LOADING_REDUCTION_FORBIDDEN); return id;
    }
    private static RdLoadingTaskDO requireTask(RdLoadingTaskDO task, Long tenantId) {
        if (task == null || !Objects.equals(task.getTenantId(), tenantId)) throw exception(LOADING_TASK_NOT_EXISTS); return task;
    }
    private void authorize(Long actorId, String... permissions) {
        if (actorId == null || permissionService.hasAnyRoles(actorId, "rd_forklift_driver")
                || !permissionService.hasAnyPermissions(actorId, permissions)) throw exception(LOADING_REDUCTION_FORBIDDEN);
    }
}
