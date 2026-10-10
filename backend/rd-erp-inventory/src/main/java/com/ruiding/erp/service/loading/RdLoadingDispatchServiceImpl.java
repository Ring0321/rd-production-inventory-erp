package com.ruiding.erp.service.loading;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import com.ruiding.erp.controller.admin.loading.vo.*;
import com.ruiding.erp.dal.dataobject.loading.*;
import com.ruiding.erp.dal.dataobject.masterdata.RdWarehouseLocationDO;
import com.ruiding.erp.dal.dataobject.order.RdOrderFulfillmentDO;
import com.ruiding.erp.dal.dataobject.order.RdSaleOrderDO;
import com.ruiding.erp.dal.dataobject.outbound.RdOutboundOrderDO;
import com.ruiding.erp.dal.dataobject.outbound.RdOutboundOrderLineDO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageTaskEventDO;
import com.ruiding.erp.service.packageunit.RdPackageTaskHistoryService;
import com.ruiding.erp.dal.dataobject.putaway.RdLocationCapacitySummaryDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockLotDO;
import com.ruiding.erp.dal.mysql.loading.*;
import com.ruiding.erp.dal.mysql.masterdata.RdWarehouseLocationMapper;
import com.ruiding.erp.dal.mysql.order.RdOrderFulfillmentMapper;
import com.ruiding.erp.dal.mysql.order.RdSaleOrderMapper;
import com.ruiding.erp.dal.mysql.outbound.RdOutboundOrderLineMapper;
import com.ruiding.erp.dal.mysql.outbound.RdOutboundOrderMapper;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.dal.mysql.stock.RdStockLotMapper;
import com.ruiding.erp.enums.RdLoadingTaskActionEnum;
import com.ruiding.erp.enums.RdLoadingTaskActorEnum;
import com.ruiding.erp.enums.RdLoadingTaskStatusEnum;
import com.ruiding.erp.enums.RdOrderFulfillmentStatusEnum;
import com.ruiding.erp.enums.RdOrderStatusEnum;
import com.ruiding.erp.enums.RdOutboundOrderStatusEnum;
import com.ruiding.erp.enums.RdPackageStatusEnum;
import com.ruiding.erp.service.outbound.RdOutboundOrderService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.ruiding.erp.enums.ErrorCodeConstants.*;

@Service
public class RdLoadingDispatchServiceImpl implements RdLoadingDispatchService {
    private static final String DRIVER_ROLE = "rd_forklift_driver";
    private static final String LINE_PLANNED = "PLANNED";
    private static final String LINE_SCANNED = "SCANNED";
    private static final String LINE_EXCEPTION = "EXCEPTION";
    private static final String LINE_CANCELLED = "CANCELLED";
    private static final String LINE_OUTBOUNDED = "OUTBOUNDED";
    private static final String SCAN_DEVICE_LOADING_TASK = "LOADING_TASK";
    private static final String EXCEPTION_STATUS_OPEN = "OPEN";
    private static final String EXCEPTION_STATUS_RESOLVED = "RESOLVED";
    private static final String RESOLUTION_RETURN_TO_DRIVER = "RETURN_TO_DRIVER";
    private static final String RESOLUTION_CANCEL = "CANCEL";
    private static final String RESOLUTION_REPLACE_PACKAGE = "REPLACE_PACKAGE";
    private static final Set<String> LOADING_EXCEPTION_TYPES = Set.of(
            "MISSING_PACKAGE", "LOCATION_MISMATCH", "WRONG_PACKAGE",
            "LABEL_DAMAGED", "VEHICLE_CHANGED", "OTHER");
    private static final Set<String> LOADING_EXCEPTION_RESOLUTION_ACTIONS = Set.of(
            RESOLUTION_RETURN_TO_DRIVER, RESOLUTION_CANCEL, RESOLUTION_REPLACE_PACKAGE);
    private static final Set<String> PHYSICAL_STOCK_STATUSES = Set.of(
            RdPackageStatusEnum.IN_STOCK.getStatus(),
            RdPackageStatusEnum.OUTBOUND_PENDING.getStatus());

    @Resource private RdPickupSlipMapper slipMapper;
    @Resource private RdPickupSlipLineMapper slipLineMapper;
    @Resource private RdLoadingTaskMapper taskMapper;
    @Resource private RdLoadingTaskLineMapper taskLineMapper;
    @Resource private RdLoadingScanRecordMapper scanRecordMapper;
    @Resource private RdLoadingExceptionMapper exceptionMapper;
    @Resource private RdLoadingPackageClaimMapper claimMapper;
    @Resource private RdPackageUnitMapper packageMapper;
    @Resource private RdStockLotMapper stockLotMapper;
    @Resource private RdWarehouseLocationMapper locationMapper;
    @Resource private RdSaleOrderMapper saleOrderMapper;
    @Resource private RdOrderFulfillmentMapper fulfillmentMapper;
    @Resource private RdOutboundOrderMapper outboundOrderMapper;
    @Resource private RdOutboundOrderLineMapper outboundOrderLineMapper;
    @Resource private RdOutboundOrderService outboundOrderService;
    @Resource private AdminUserService userService;
    @Resource private PermissionService permissionService;
    @Resource private RdPackageTaskHistoryService taskHistoryService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPickupSlip(RdPickupSlipCreateReqVO req, Long issuerId, String issuerName) {
        if (slipMapper.selectOne(RdPickupSlipDO::getSlipNo, req.getSlipNo().trim()) != null) {
            throw exception(PICKUP_SLIP_DUPLICATE);
        }
        Set<Integer> lineNumbers = new HashSet<>();
        int count = 0;
        BigDecimal weight = BigDecimal.ZERO;
        boolean allCounts = true;
        boolean allWeights = true;
        for (RdPickupSlipCreateReqVO.Line line : req.getLines()) {
            if (line == null || line.getLineNo() == null || line.getLineNo() <= 0
                    || !lineNumbers.add(line.getLineNo())
                    || !positive(line.getRequestedPackageCount()) && !positive(line.getRequestedWeight())
                    || line.getRequestedPackageCount() != null && !positive(line.getRequestedPackageCount())
                    || line.getRequestedWeight() != null && !positive(line.getRequestedWeight())) {
                throw exception(PICKUP_SLIP_INVALID);
            }
            if (line.getSaleOrderId() != null) {
                RdSaleOrderDO order = saleOrderMapper.selectById(line.getSaleOrderId());
                if (order == null || !Objects.equals(order.getCustomerName(), req.getCustomerName())
                        || !Objects.equals(order.getProductModel(), line.getProductModel())
                        || RdOrderStatusEnum.CLOSED.getStatus().equals(order.getStatus())
                        || RdOrderStatusEnum.CANCELLED.getStatus().equals(order.getStatus())) {
                    throw exception(PICKUP_SLIP_ORDER_MISMATCH);
                }
            }
            allCounts &= line.getRequestedPackageCount() != null;
            allWeights &= line.getRequestedWeight() != null;
            count += line.getRequestedPackageCount() == null ? 0 : line.getRequestedPackageCount();
            weight = weight.add(line.getRequestedWeight() == null ? BigDecimal.ZERO : line.getRequestedWeight());
        }
        if (req.getRequestedPackageCount() != null && (!positive(req.getRequestedPackageCount())
                || allCounts && req.getRequestedPackageCount() != count)
                || req.getRequestedWeight() != null && (!positive(req.getRequestedWeight())
                || allWeights && req.getRequestedWeight().compareTo(weight) != 0)) {
            throw exception(PICKUP_SLIP_INVALID);
        }
        RdPickupSlipDO slip = new RdPickupSlipDO();
        slip.setSlipNo(req.getSlipNo().trim());
        slip.setStatus("ISSUED");
        slip.setCustomerName(req.getCustomerName().trim());
        slip.setPickupDate(req.getPickupDate());
        slip.setVehiclePlate(req.getVehiclePlate());
        slip.setTransportDriverName(req.getTransportDriverName());
        slip.setTransportDriverPhone(req.getTransportDriverPhone());
        slip.setRequestedPackageCount(req.getRequestedPackageCount() != null
                ? req.getRequestedPackageCount() : allCounts ? count : null);
        slip.setRequestedWeight(req.getRequestedWeight() != null
                ? req.getRequestedWeight() : allWeights ? weight : null);
        slip.setSourceAttachmentRef(req.getSourceAttachmentRef());
        slip.setIssuerId(issuerId);
        slip.setIssuerName(issuerName);
        slip.setIssuedTime(LocalDateTime.now());
        slip.setVersion(0);
        slip.setRemark(req.getRemark());
        try {
            slipMapper.insert(slip);
        } catch (DuplicateKeyException ex) {
            throw exception(PICKUP_SLIP_DUPLICATE);
        }
        for (RdPickupSlipCreateReqVO.Line line : req.getLines()) {
            RdPickupSlipLineDO item = new RdPickupSlipLineDO();
            item.setSlipId(slip.getId());
            item.setLineNo(line.getLineNo());
            item.setSaleOrderId(line.getSaleOrderId());
            item.setProductName(line.getProductName());
            item.setProductModel(line.getProductModel().trim());
            item.setFurnaceNo(blankToNull(line.getFurnaceNo()));
            item.setFurnaceLayer(blankToNull(line.getFurnaceLayer()));
            item.setRequestedPackageCount(line.getRequestedPackageCount());
            item.setRequestedWeight(line.getRequestedWeight());
            item.setRemark(line.getRemark());
            slipLineMapper.insert(item);
        }
        return slip.getId();
    }

    @Override
    public PageResult<RdPickupSlipDO> getPickupSlipPage(RdPickupSlipPageReqVO req) {
        return slipMapper.selectPage(req, new LambdaQueryWrapperX<RdPickupSlipDO>()
                .likeIfPresent(RdPickupSlipDO::getSlipNo, req.getSlipNo())
                .likeIfPresent(RdPickupSlipDO::getCustomerName, req.getCustomerName())
                .eqIfPresent(RdPickupSlipDO::getStatus, req.getStatus())
                .orderByDesc(RdPickupSlipDO::getId));
    }

    @Override
    public RdPickupSlipDetailRespVO getPickupSlip(Long id) {
        RdPickupSlipDO slip = slipMapper.selectById(id);
        if (slip == null) { throw exception(PICKUP_SLIP_NOT_EXISTS); }
        List<RdPickupSlipLineDO> lines = slipLineMapper.selectListBySlipId(id);
        lines.sort(Comparator.comparing(RdPickupSlipLineDO::getLineNo));
        return new RdPickupSlipDetailRespVO(slip, lines);
    }

    @Override
    public PageResult<RdPackageUnitDO> getAvailablePackagePage(RdLoadingPackagePageReqVO req) {
        LambdaQueryWrapperX<RdPackageUnitDO> query = new LambdaQueryWrapperX<RdPackageUnitDO>()
                .eq(RdPackageUnitDO::getStatus, RdPackageStatusEnum.IN_STOCK.getStatus())
                .eqIfPresent(RdPackageUnitDO::getCustomerName, req.getCustomerName())
                .eqIfPresent(RdPackageUnitDO::getProductModel, req.getProductModel())
                .eqIfPresent(RdPackageUnitDO::getFurnaceNo, req.getFurnaceNo())
                .eqIfPresent(RdPackageUnitDO::getFurnaceLayer, req.getFurnaceLayer())
                .eqIfPresent(RdPackageUnitDO::getLocationId, req.getLocationId());
        query.orderByAsc(RdPackageUnitDO::getLocationCode).orderByAsc(RdPackageUnitDO::getPackageCode);
        if (req.getKeyword() != null && !req.getKeyword().isBlank()) {
            query.and(item -> item.like(RdPackageUnitDO::getPackageCode, req.getKeyword())
                    .or().like(RdPackageUnitDO::getLocationCode, req.getKeyword()));
        }
        if (req.getSaleOrderId() != null) {
            RdSaleOrderDO order = saleOrderMapper.selectById(req.getSaleOrderId());
            if (order == null) { return PageResult.empty(); }
            List<Long> lotIds = fulfillmentMapper.selectList(new LambdaQueryWrapperX<RdOrderFulfillmentDO>()
                            .eq(RdOrderFulfillmentDO::getOrderId, order.getId())
                            .eq(RdOrderFulfillmentDO::getStatus,
                                    RdOrderFulfillmentStatusEnum.ALLOCATED.getStatus()))
                    .stream().map(RdOrderFulfillmentDO::getStockLotId).distinct().collect(Collectors.toList());
            if (lotIds.isEmpty()) { return PageResult.empty(); }
            query.eq(RdPackageUnitDO::getCustomerName, order.getCustomerName())
                    .eq(RdPackageUnitDO::getProductModel, order.getProductModel())
                    .in(RdPackageUnitDO::getStockLotId, lotIds);
        }
        // The claim table has no logical deletion: it represents only current reservations.
        query.notExists("SELECT 1 FROM rd_loading_package_claim c WHERE c.package_id = rd_package_unit.id "
                + "AND c.tenant_id = rd_package_unit.tenant_id");
        return packageMapper.selectPage(req, query);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTask(RdLoadingTaskCreateReqVO req, Long keeperId, String keeperName) {
        RdPickupSlipDO slip = slipMapper.selectByIdForUpdate(req.getPickupSlipId(), TenantContextHolder.getTenantId());
        if (slip == null || !"ISSUED".equals(slip.getStatus())) {
            throw exception(PICKUP_SLIP_NOT_EXISTS);
        }
        Long active = taskMapper.selectCount(new LambdaQueryWrapperX<RdLoadingTaskDO>()
                .eq(RdLoadingTaskDO::getPickupSlipId, slip.getId())
                .ne(RdLoadingTaskDO::getStatus, RdLoadingTaskStatusEnum.CANCELLED.getStatus()));
        if (active > 0) { throw exception(LOADING_TASK_ALREADY_ASSIGNED); }
        AdminUserDO driver = userService.getUser(req.getForkliftDriverId());
        if (driver == null || driver.getStatus() == null || driver.getStatus() != 0
                || !permissionService.hasAnyRoles(driver.getId(), DRIVER_ROLE)) {
            throw exception(LOADING_DRIVER_INVALID);
        }
        Map<Long, RdPickupSlipLineDO> slipLines = slipLineMapper.selectListBySlipId(slip.getId()).stream()
                .collect(Collectors.toMap(RdPickupSlipLineDO::getId, item -> item));
        Set<Long> ids = new HashSet<>();
        Map<Long, Integer> lineCounts = new HashMap<>();
        Map<Long, BigDecimal> lineWeights = new HashMap<>();
        for (RdLoadingTaskCreateReqVO.Selection selection : req.getSelections()) {
            if (selection == null || selection.getPackageId() == null
                    || !slipLines.containsKey(selection.getPickupSlipLineId())
                    || !ids.add(selection.getPackageId())) {
                throw exception(LOADING_PACKAGE_INVALID);
            }
        }
        Map<Long, RdPackageUnitDO> packages = new HashMap<>();
        Map<Long, RdOrderFulfillmentDO> fulfillments = new HashMap<>();
        Map<Long, Integer> fulfillmentCounts = new HashMap<>();
        for (Long packageId : ids.stream().sorted().collect(Collectors.toList())) {
            RdPackageUnitDO unit = packageMapper.selectByIdForUpdate(packageId);
            if (unit == null || !Objects.equals(unit.getTenantId(), slip.getTenantId())
                    || !RdPackageStatusEnum.IN_STOCK.getStatus().equals(unit.getStatus())
                    || unit.getLocationId() == null || unit.getStockLotId() == null
                    || unit.getPackageWeight() == null || unit.getPackageWeight().signum() <= 0
                    || !Objects.equals(unit.getCustomerName(), slip.getCustomerName())) {
                throw exception(LOADING_PACKAGE_INVALID);
            }
            RdStockLotDO lot = stockLotMapper.selectById(unit.getStockLotId());
            RdWarehouseLocationDO location = locationMapper.selectById(unit.getLocationId());
            if (lot == null || location == null || !Boolean.TRUE.equals(location.getEnabled())
                    || Boolean.TRUE.equals(location.getFrozen())
                    || !Objects.equals(location.getLocationCode(), unit.getLocationCode())
                    || !Objects.equals(lot.getLocationId(), unit.getLocationId())
                    || !Objects.equals(lot.getCustomerName(), unit.getCustomerName())
                    || !Objects.equals(lot.getProductModel(), unit.getProductModel())
                    || !Objects.equals(lot.getFurnaceNo(), unit.getFurnaceNo())
                    || !Objects.equals(lot.getFurnaceLayer(), unit.getFurnaceLayer())) {
                throw exception(LOADING_PACKAGE_INVALID);
            }
            packages.put(packageId, unit);
        }
        for (RdLoadingTaskCreateReqVO.Selection selection : req.getSelections()) {
            RdPickupSlipLineDO line = slipLines.get(selection.getPickupSlipLineId());
            RdPackageUnitDO unit = packages.get(selection.getPackageId());
            if (!Objects.equals(line.getProductModel(), unit.getProductModel())
                    || line.getFurnaceNo() != null && !Objects.equals(line.getFurnaceNo(), unit.getFurnaceNo())
                    || line.getFurnaceLayer() != null && !Objects.equals(line.getFurnaceLayer(), unit.getFurnaceLayer())) {
                throw exception(LOADING_PACKAGE_INVALID);
            }
            if (line.getSaleOrderId() != null) {
                RdSaleOrderDO order = saleOrderMapper.selectById(line.getSaleOrderId());
                if (order == null || !Objects.equals(order.getCustomerName(), slip.getCustomerName())
                        || !Objects.equals(order.getProductModel(), unit.getProductModel())
                        || RdOrderStatusEnum.CLOSED.getStatus().equals(order.getStatus())
                        || RdOrderStatusEnum.CANCELLED.getStatus().equals(order.getStatus())) {
                    throw exception(PICKUP_SLIP_ORDER_MISMATCH);
                }
                RdOrderFulfillmentDO fulfillment = fulfillmentMapper.selectActive(order.getId(), unit.getStockLotId());
                if (fulfillment == null || !RdOrderFulfillmentStatusEnum.ALLOCATED.getStatus()
                        .equals(fulfillment.getStatus())) {
                    throw exception(LOADING_FULFILLMENT_INSUFFICIENT);
                }
                fulfillments.put(unit.getId(), fulfillment);
                fulfillmentCounts.merge(fulfillment.getId(), 1, Integer::sum);
            }
            lineCounts.merge(line.getId(), 1, Integer::sum);
            lineWeights.merge(line.getId(), unit.getPackageWeight(), BigDecimal::add);
        }
        int totalCount = 0;
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (RdPickupSlipLineDO line : slipLines.values()) {
            int count = lineCounts.getOrDefault(line.getId(), 0);
            BigDecimal weight = lineWeights.getOrDefault(line.getId(), BigDecimal.ZERO);
            if (count == 0 || line.getRequestedPackageCount() != null
                    && line.getRequestedPackageCount() != count
                    || line.getRequestedWeight() != null
                    && line.getRequestedWeight().compareTo(weight) != 0) {
                throw exception(LOADING_QUANTITY_MISMATCH);
            }
            totalCount += count;
            totalWeight = totalWeight.add(weight);
        }
        if (slip.getRequestedPackageCount() != null && slip.getRequestedPackageCount() != totalCount
                || slip.getRequestedWeight() != null && slip.getRequestedWeight().compareTo(totalWeight) != 0) {
            throw exception(LOADING_QUANTITY_MISMATCH);
        }
        for (Map.Entry<Long, Integer> entry : fulfillmentCounts.entrySet()) {
            RdOrderFulfillmentDO fulfillment = fulfillments.values().stream()
                    .filter(item -> item.getId().equals(entry.getKey())).findFirst().orElseThrow();
            BigDecimal remaining = fulfillment.getAllocatedQty().subtract(
                    fulfillment.getOutboundQty() == null ? BigDecimal.ZERO : fulfillment.getOutboundQty());
            if (remaining.compareTo(BigDecimal.valueOf(entry.getValue())) < 0) {
                throw exception(LOADING_FULFILLMENT_INSUFFICIENT);
            }
        }
        RdLoadingTaskDO task = new RdLoadingTaskDO();
        task.setTaskNo("LD" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        task.setPickupSlipId(slip.getId());
        task.setPickupSlipNo(slip.getSlipNo());
        task.setStatus(RdLoadingTaskStatusMachine.transition(RdLoadingTaskStatusEnum.PENDING_ASSIGNMENT,
                RdLoadingTaskActionEnum.ASSIGN, RdLoadingTaskActorEnum.WAREHOUSE_KEEPER).getStatus());
        task.setCustomerName(slip.getCustomerName());
        task.setVehiclePlate(slip.getVehiclePlate());
        task.setTransportDriverName(slip.getTransportDriverName());
        task.setTransportDriverPhone(slip.getTransportDriverPhone());
        task.setWarehouseKeeperId(keeperId);
        task.setWarehouseKeeperName(keeperName);
        task.setForkliftDriverId(driver.getId());
        task.setForkliftDriverName(driver.getNickname());
        task.setPlannedPackageCount(totalCount);
        task.setScannedPackageCount(0);
        task.setPlannedWeight(totalWeight);
        task.setScannedWeight(BigDecimal.ZERO);
        task.setPlanVersion(1);
        task.setVersion(0);
        task.setAssignedTime(LocalDateTime.now());
        task.setRemark(req.getRemark());
        taskMapper.insert(task);
        int index = 1;
        for (RdLoadingTaskCreateReqVO.Selection selection : req.getSelections()) {
            RdPickupSlipLineDO slipLine = slipLines.get(selection.getPickupSlipLineId());
            RdPackageUnitDO unit = packages.get(selection.getPackageId());
            RdOrderFulfillmentDO fulfillment = fulfillments.get(unit.getId());
            RdLoadingTaskLineDO line = new RdLoadingTaskLineDO();
            line.setTaskId(task.getId());
            line.setLineNo(index++);
            line.setPickupSlipLineId(slipLine.getId());
            line.setPackageId(unit.getId());
            line.setPackageCode(unit.getPackageCode());
            line.setSaleOrderId(slipLine.getSaleOrderId());
            line.setFulfillmentId(fulfillment == null ? null : fulfillment.getId());
            line.setStockLotId(unit.getStockLotId());
            line.setPlannedLocationId(unit.getLocationId());
            line.setPlannedLocationCode(unit.getLocationCode());
            line.setCustomerName(unit.getCustomerName());
            line.setProductModel(unit.getProductModel());
            line.setFurnaceNo(unit.getFurnaceNo());
            line.setFurnaceLayer(unit.getFurnaceLayer());
            line.setPackageWeight(unit.getPackageWeight());
            line.setLineStatus(LINE_PLANNED);
            line.setPlanVersion(1);
            line.setVersion(0);
            taskLineMapper.insert(line);
            RdLoadingPackageClaimDO claim = new RdLoadingPackageClaimDO();
            claim.setTenantId(slip.getTenantId());
            claim.setPackageId(unit.getId());
            claim.setTaskId(task.getId());
            claim.setTaskLineId(line.getId());
            claim.setClaimedTime(LocalDateTime.now());
            try {
                claimMapper.insert(claim);
            } catch (DuplicateKeyException ex) {
                throw exception(LOADING_PACKAGE_CLAIMED);
            }
            taskHistoryService.append(taskEvent(task, line, "ASSIGNED", task.getAssignedTime(),
                    keeperId, keeperName).setDetail("提货单 " + task.getPickupSlipNo()
                            + " / 叉车司机：" + task.getForkliftDriverName()));
        }
        slip.setStatus("DISPATCHED");
        slip.setVersion(slip.getVersion() == null ? 1 : slip.getVersion() + 1);
        slipMapper.updateById(slip);
        return task.getId();
    }

    @Override
    public RdLoadingBatchScanRespVO previewBatchScan(RdLoadingBatchScanPreviewReqVO req) {
        String packageCode = normalizePackageCode(req.getPackageCode());
        RdLoadingTaskLineDO matched = findLoadingLineForPackage(req.getTaskId(), packageCode);
        if (matched == null) {
            throw exception(LOADING_PACKAGE_NOT_IN_TASK);
        }
        RdLoadingTaskDO task = taskMapper.selectById(matched.getTaskId());
        if (task == null) {
            throw exception(LOADING_TASK_NOT_EXISTS);
        }
        List<RdLoadingTaskLineDO> lines = sortedTaskLines(task.getId());
        return buildBatchScanResponse(null, task, lines, matched, packageCode,
                "PREVIEW", true, false, 0, BigDecimal.ZERO,
                "已识别装车任务，可核对后批量确认实际已装包件");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RdLoadingBatchScanRespVO confirmBatchScan(RdLoadingBatchScanConfirmReqVO req,
                                                     Long keeperId,
                                                     String keeperName) {
        String packageCode = normalizePackageCode(req.getRepresentativePackageCode());
        RdLoadingTaskDO task = taskMapper.selectByIdForUpdate(req.getTaskId());
        if (task == null) {
            throw exception(LOADING_TASK_NOT_EXISTS);
        }
        RdLoadingScanRecordDO replay = scanRecordMapper.selectByTaskAndIdempotencyKey(
                task.getId(), req.getIdempotencyKey());
        if (replay != null) {
            List<RdLoadingTaskLineDO> replayLines = sortedTaskLines(task.getId());
            RdLoadingTaskLineDO replayLine = replayLines.stream()
                    .filter(item -> Objects.equals(item.getId(), replay.getTaskLineId()))
                    .findFirst().orElse(null);
            return buildBatchScanResponse(replay, task, replayLines, replayLine,
                    replay.getPackageCode(), replay.getScanResult(), replay.getSuccess(), true,
                    0, BigDecimal.ZERO, "重复提交已识别，已返回上一次装车扫码结果");
        }

        String statusBefore = task.getStatus();
        RdLoadingTaskStatusEnum current = parseLoadingTaskStatus(task.getStatus());
        if (current != RdLoadingTaskStatusEnum.ASSIGNED && current != RdLoadingTaskStatusEnum.LOADING) {
            throw exception(LOADING_TASK_STATUS_INVALID);
        }

        List<RdLoadingTaskLineDO> lines = new ArrayList<>(taskLineMapper.selectListByTaskIdForUpdate(task.getId()));
        lines.sort(Comparator.comparing(RdLoadingTaskLineDO::getLineNo));
        RdLoadingTaskLineDO representative = lines.stream()
                .filter(item -> Objects.equals(normalizeCode(item.getPackageCode()), normalizeCode(packageCode)))
                .findFirst().orElse(null);
        if (representative == null) {
            throw exception(LOADING_PACKAGE_NOT_IN_TASK);
        }

        Set<Long> selectedIds = new HashSet<>(req.getTaskLineIds());
        if (selectedIds.size() != req.getTaskLineIds().size()) {
            throw exception(LOADING_PACKAGE_INVALID);
        }
        List<RdLoadingTaskLineDO> selectedLines = lines.stream()
                .filter(item -> selectedIds.contains(item.getId()))
                .collect(Collectors.toList());
        if (selectedLines.size() != selectedIds.size()) {
            throw exception(LOADING_PACKAGE_NOT_IN_TASK);
        }
        List<RdLoadingTaskLineDO> toConfirm = selectedLines.stream()
                .filter(item -> !countedAsScanned(item.getLineStatus()))
                .collect(Collectors.toList());
        if (toConfirm.isEmpty()) {
            throw exception(LOADING_PACKAGE_DUPLICATE);
        }

        LocalDateTime now = LocalDateTime.now();
        BigDecimal confirmedWeight = BigDecimal.ZERO;
        for (RdLoadingTaskLineDO line : toConfirm) {
            if (!LINE_PLANNED.equals(line.getLineStatus())) {
                throw exception(LOADING_TASK_STATUS_INVALID);
            }
            line.setLineStatus(LINE_SCANNED);
            line.setScanTime(now);
            line.setVersion(nextVersion(line.getVersion()));
            taskLineMapper.updateById(line);
            confirmedWeight = confirmedWeight.add(zero(line.getPackageWeight()));
        }

        if (current == RdLoadingTaskStatusEnum.ASSIGNED) {
            task.setStatus(RdLoadingTaskStatusMachine.transition(current,
                    RdLoadingTaskActionEnum.START, RdLoadingTaskActorEnum.WAREHOUSE_KEEPER).getStatus());
            if (task.getStartTime() == null) {
                task.setStartTime(now);
            }
        }
        int scanned = (int) lines.stream().filter(item -> countedAsScanned(item.getLineStatus())).count();
        BigDecimal scannedWeight = lines.stream()
                .filter(item -> countedAsScanned(item.getLineStatus()))
                .map(item -> zero(item.getPackageWeight()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        task.setScannedPackageCount(scanned);
        task.setScannedWeight(scannedWeight);
        if (scanned == zero(task.getPlannedPackageCount())) {
            task.setStatus(RdLoadingTaskStatusMachine.transition(parseLoadingTaskStatus(task.getStatus()),
                    RdLoadingTaskActionEnum.COMPLETE_SCANNING,
                    RdLoadingTaskActorEnum.WAREHOUSE_KEEPER).getStatus());
            task.setScanCompleteTime(now);
        }
        task.setVersion(nextVersion(task.getVersion()));
        taskMapper.updateById(task);

        RdLoadingScanRecordDO record = insertScanRecord(req, task, representative, packageCode,
                "BATCH_CONFIRMED", true, statusBefore, task.getStatus(), keeperId, keeperName, now);
        for (RdLoadingTaskLineDO line : toConfirm) {
            taskHistoryService.append(taskEvent(task, line, "SCAN_CONFIRMED", now, keeperId, keeperName)
                    .setSourceType("LOADING_SCAN").setSourceId(record.getId())
                    .setSourceVersion(zero(task.getPlanVersion()))
                    .setDetail("提货单 " + task.getPickupSlipNo() + " / 计划库位核对，尚非出库过账"));
        }
        return buildBatchScanResponse(record, task, lines, representative, packageCode,
                record.getScanResult(), true, false, toConfirm.size(), confirmedWeight,
                "已按代表码批量确认 " + toConfirm.size() + " 包，仍保留每包独立二维码记录");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RdLoadingConfirmOutboundRespVO confirmOutbound(RdLoadingConfirmOutboundReqVO req,
                                                          Long keeperId,
                                                          String keeperName) {
        RdLoadingTaskDO task = taskMapper.selectByIdForUpdate(req.getTaskId());
        if (task == null) {
            throw exception(LOADING_TASK_NOT_EXISTS);
        }
        if (req.getVersion() != null && !Objects.equals(zero(task.getVersion()), req.getVersion())) {
            throw exception(LOADING_TASK_VERSION_CONFLICT);
        }
        RdLoadingTaskStatusEnum current = parseLoadingTaskStatus(task.getStatus());
        if (current != RdLoadingTaskStatusEnum.PENDING_REVIEW) {
            throw exception(LOADING_TASK_STATUS_INVALID);
        }
        if (!exceptionMapper.selectOpenListByTaskIdForUpdate(task.getId()).isEmpty()) {
            throw exception(LOADING_CONFIRM_HAS_OPEN_EXCEPTION);
        }
        List<RdLoadingTaskLineDO> lines = new ArrayList<>(taskLineMapper.selectListByTaskIdForUpdate(task.getId()));
        lines.sort(Comparator.comparing(RdLoadingTaskLineDO::getLineNo));
        if (lines.isEmpty() || lines.stream().anyMatch(line -> !LINE_SCANNED.equals(line.getLineStatus()))
                || lines.stream().anyMatch(line -> line.getFulfillmentId() == null
                || line.getSaleOrderId() == null || line.getStockLotId() == null
                || line.getPackageId() == null || !positive(line.getPackageWeight()))) {
            throw exception(LOADING_CONFIRM_PACKAGES_INCOMPLETE);
        }

        LocalDateTime now = LocalDateTime.now();
        List<Long> outboundOrderIds = new ArrayList<>();
        Set<Long> touchedLocationIds = new HashSet<>();
        int totalPackages = 0;
        BigDecimal totalWeight = BigDecimal.ZERO;
        int orderIndex = 1;
        Map<Long, List<RdLoadingTaskLineDO>> linesByFulfillment = lines.stream()
                .collect(Collectors.groupingBy(RdLoadingTaskLineDO::getFulfillmentId));
        for (Map.Entry<Long, List<RdLoadingTaskLineDO>> entry : linesByFulfillment.entrySet()) {
            RdOrderFulfillmentDO fulfillment = fulfillmentMapper.selectByIdForUpdate(entry.getKey());
            if (fulfillment == null || fulfillment.getOrderId() == null || fulfillment.getStockLotId() == null) {
                throw exception(LOADING_FULFILLMENT_INSUFFICIENT);
            }
            RdStockLotDO stockLot = stockLotMapper.selectByIdForUpdate(fulfillment.getStockLotId());
            if (stockLot == null) {
                throw exception(STOCK_LOT_NOT_EXISTS);
            }

            List<RdLoadingTaskLineDO> groupLines = entry.getValue();
            RdOutboundOrderDO outboundOrder = buildLoadingOutboundOrder(task, fulfillment, stockLot,
                    groupLines, keeperId, keeperName, now, orderIndex++, req.getRemark());
            outboundOrderMapper.insert(outboundOrder);

            for (RdLoadingTaskLineDO line : groupLines) {
                RdPackageUnitDO unit = packageMapper.selectByIdForUpdate(line.getPackageId());
                validateLoadingLineForOutbound(task, line, unit, fulfillment);
                RdLoadingPackageClaimDO claim = claimMapper.selectByPackageIdForUpdate(line.getPackageId());
                if (claim == null || !Objects.equals(claim.getTaskId(), task.getId())
                        || !Objects.equals(claim.getTaskLineId(), line.getId())) {
                    throw exception(LOADING_PACKAGE_INVALID);
                }
                touchedLocationIds.add(unit.getLocationId());
                unit.setStatus(RdPackageStatusEnum.OUTBOUND_PENDING.getStatus());
                unit.setVersion(nextVersion(unit.getVersion()));
                packageMapper.updateById(unit);

                RdOutboundOrderLineDO outboundLine = buildLoadingOutboundLine(outboundOrder, line,
                        unit, keeperId, keeperName);
                outboundOrderLineMapper.insert(outboundLine);
                line.setOutboundOrderId(outboundOrder.getId());
                line.setOutboundOrderLineId(outboundLine.getId());
                line.setVersion(nextVersion(line.getVersion()));
                taskLineMapper.updateById(line);
            }

            outboundOrderService.postOutboundOrder(outboundOrder.getId(), null, keeperId, keeperName);
            Map<Long, RdOutboundOrderLineDO> postedLineMap = outboundOrderLineMapper
                    .selectListByOrderId(outboundOrder.getId()).stream()
                    .collect(Collectors.toMap(RdOutboundOrderLineDO::getPackageId, item -> item));
            for (RdLoadingTaskLineDO line : groupLines) {
                RdOutboundOrderLineDO postedLine = postedLineMap.get(line.getPackageId());
                if (postedLine == null || postedLine.getStockTransactionId() == null) {
                    throw exception(OUTBOUND_ORDER_STATUS_INVALID);
                }
                line.setLineStatus(LINE_OUTBOUNDED);
                line.setStockTransactionId(postedLine.getStockTransactionId());
                line.setVersion(nextVersion(line.getVersion()));
                taskLineMapper.updateById(line);
            }
            outboundOrderIds.add(outboundOrder.getId());
            totalPackages += groupLines.size();
            totalWeight = totalWeight.add(groupLines.stream()
                    .map(item -> zero(item.getPackageWeight()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        }

        for (RdLoadingPackageClaimDO claim : claimMapper.selectListByTaskIdForUpdate(task.getId())) {
            claimMapper.deleteById(claim.getId());
        }
        reconcileLocationOccupancy(touchedLocationIds);

        task.setStatus(RdLoadingTaskStatusMachine.transition(current,
                RdLoadingTaskActionEnum.CONFIRM_OUTBOUND, RdLoadingTaskActorEnum.WAREHOUSE_KEEPER).getStatus());
        task.setReviewerId(keeperId);
        task.setReviewerName(keeperName);
        task.setScannedPackageCount(totalPackages);
        task.setScannedWeight(totalWeight);
        task.setConfirmTime(now);
        task.setVersion(nextVersion(task.getVersion()));
        taskMapper.updateById(task);

        RdPickupSlipDO slip = slipMapper.selectByIdForUpdate(task.getPickupSlipId(), TenantContextHolder.getTenantId());
        if (slip == null) {
            throw exception(PICKUP_SLIP_NOT_EXISTS);
        }
        slip.setStatus("SHIPPED");
        slip.setVersion(nextVersion(slip.getVersion()));
        slipMapper.updateById(slip);

        RdLoadingConfirmOutboundRespVO resp = new RdLoadingConfirmOutboundRespVO();
        resp.setTaskId(task.getId());
        resp.setTaskNo(task.getTaskNo());
        resp.setOutboundOrderIds(outboundOrderIds);
        resp.setOutboundPackageCount(totalPackages);
        resp.setOutboundWeight(totalWeight);
        resp.setMessage("已确认出库 " + totalPackages + " 包，生成并过账 "
                + outboundOrderIds.size() + " 张出库单");
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long reportException(RdLoadingExceptionCreateReqVO req, Long driverId, String driverName) {
        if (!LOADING_EXCEPTION_TYPES.contains(req.getExceptionType())) {
            throw exception(LOADING_EXCEPTION_TYPE_INVALID);
        }
        RdLoadingTaskDO task = taskMapper.selectByIdForUpdate(req.getTaskId());
        if (task == null || !Objects.equals(task.getForkliftDriverId(), driverId)) {
            throw exception(LOADING_TASK_NOT_EXISTS);
        }
        RdLoadingTaskStatusEnum current = parseLoadingTaskStatus(task.getStatus());
        if (!RdLoadingTaskStatusMachine.canTransition(current,
                RdLoadingTaskActionEnum.REPORT_EXCEPTION, RdLoadingTaskActorEnum.FORKLIFT_DRIVER)) {
            throw exception(LOADING_TASK_STATUS_INVALID);
        }
        List<RdLoadingTaskLineDO> lines = taskLineMapper.selectListByTaskIdForUpdate(task.getId());
        RdLoadingTaskLineDO line = resolveLoadingExceptionLine(req.getTaskLineId(),
                req.getPackageCode(), lines);
        LocalDateTime now = LocalDateTime.now();
        RdLoadingExceptionDO item = new RdLoadingExceptionDO();
        item.setExceptionNo(generateLoadingExceptionNo());
        item.setTaskId(task.getId());
        item.setTaskLineId(line == null ? null : line.getId());
        item.setPackageId(line == null ? null : line.getPackageId());
        item.setPackageCode(line == null ? normalizePackageCode(req.getPackageCode()) : line.getPackageCode());
        item.setPlanVersion(task.getPlanVersion());
        item.setExceptionType(req.getExceptionType());
        item.setExceptionStatus(EXCEPTION_STATUS_OPEN);
        item.setDescription(req.getDescription());
        item.setAttachmentRefs(req.getAttachmentRefs());
        item.setObservedLocationCode(blankToNull(req.getObservedLocationCode()));
        item.setReporterId(driverId);
        item.setReporterName(driverName);
        item.setReportedTime(now);
        item.setVersion(0);
        exceptionMapper.insert(item);
        for (RdLoadingTaskLineDO affected : lines) {
            if (!LINE_CANCELLED.equals(affected.getLineStatus()) && !LINE_OUTBOUNDED.equals(affected.getLineStatus())
                    && (line == null || Objects.equals(line.getPackageId(), affected.getPackageId()))) {
                taskHistoryService.append(taskEvent(task, affected, "EXCEPTION_REPORTED", now,
                        driverId, driverName).setStage("EXCEPTION").setSourceType("LOADING_EXCEPTION")
                        .setSourceId(item.getId()).setSourceNo(item.getExceptionNo()).setSourceVersion(0)
                        .setDetail("装车任务 " + task.getTaskNo() + " / " + item.getExceptionType()
                                + " / " + item.getDescription()));
            }
        }

        if (line != null && LINE_PLANNED.equals(line.getLineStatus())) {
            line.setLineStatus(LINE_EXCEPTION);
            line.setVersion(nextVersion(line.getVersion()));
            taskLineMapper.updateById(line);
        }
        task.setStatus(RdLoadingTaskStatusMachine.transition(current,
                RdLoadingTaskActionEnum.REPORT_EXCEPTION, RdLoadingTaskActorEnum.FORKLIFT_DRIVER).getStatus());
        task.setVersion(nextVersion(task.getVersion()));
        taskMapper.updateById(task);
        return item.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resolveException(RdLoadingExceptionResolveReqVO req, Long keeperId, String keeperName) {
        if (!LOADING_EXCEPTION_RESOLUTION_ACTIONS.contains(req.getResolutionAction())) {
            throw exception(LOADING_EXCEPTION_ACTION_INVALID);
        }
        if (blankToNull(req.getHandlingResult()) == null) {
            throw exception(LOADING_EXCEPTION_HANDLE_REQUIRED);
        }
        RdLoadingTaskDO task = taskMapper.selectByIdForUpdate(req.getTaskId());
        if (task == null) {
            throw exception(LOADING_TASK_NOT_EXISTS);
        }
        if (!Objects.equals(zero(task.getVersion()), req.getVersion())) {
            throw exception(LOADING_TASK_VERSION_CONFLICT);
        }
        RdLoadingTaskStatusEnum current = parseLoadingTaskStatus(task.getStatus());
        if (current != RdLoadingTaskStatusEnum.EXCEPTION_PENDING) {
            throw exception(LOADING_TASK_STATUS_INVALID);
        }
        List<RdLoadingExceptionDO> openExceptions = exceptionMapper.selectOpenListByTaskIdForUpdate(task.getId());
        if (openExceptions.isEmpty()) {
            throw exception(LOADING_EXCEPTION_NOT_EXISTS);
        }
        List<RdLoadingTaskLineDO> lines = new ArrayList<>(taskLineMapper.selectListByTaskIdForUpdate(task.getId()));
        lines.sort(Comparator.comparing(RdLoadingTaskLineDO::getLineNo));
        LocalDateTime now = LocalDateTime.now();
        closeLoadingExceptions(task, lines, openExceptions, req, keeperId, keeperName, now);
        if (RESOLUTION_REPLACE_PACKAGE.equals(req.getResolutionAction())) {
            replaceLoadingTaskPackages(task, lines, req.getReplacements(), now, keeperId, keeperName);
            recordLoadingTaskReturn(task, lines, now, keeperId, keeperName, req.getHandlingResult());
            return;
        }
        if (RESOLUTION_RETURN_TO_DRIVER.equals(req.getResolutionAction())) {
            returnLoadingTaskToDriver(task, lines, now);
            recordLoadingTaskReturn(task, lines, now, keeperId, keeperName, req.getHandlingResult());
            return;
        }
        cancelLoadingTask(task, lines, req.getHandlingResult(), now);
        for (RdLoadingTaskLineDO line : lines) {
            if (LINE_CANCELLED.equals(line.getLineStatus())) {
                taskHistoryService.append(taskEvent(task, line, "CANCELLED", now, keeperId, keeperName)
                        .setDetail(req.getHandlingResult()));
            }
        }
    }

    @Override
    public PageResult<RdLoadingTaskDO> getTaskPage(RdLoadingTaskPageReqVO req, Long driverId) {
        LambdaQueryWrapperX<RdLoadingTaskDO> query = new LambdaQueryWrapperX<RdLoadingTaskDO>()
                .likeIfPresent(RdLoadingTaskDO::getTaskNo, req.getTaskNo())
                .likeIfPresent(RdLoadingTaskDO::getPickupSlipNo, req.getPickupSlipNo())
                .likeIfPresent(RdLoadingTaskDO::getCustomerName, req.getCustomerName())
                .eqIfPresent(RdLoadingTaskDO::getStatus, req.getStatus());
        query.orderByDesc(RdLoadingTaskDO::getId);
        if (driverId != null) {
            query.eq(RdLoadingTaskDO::getForkliftDriverId, driverId);
        } else {
            query.eqIfPresent(RdLoadingTaskDO::getForkliftDriverId, req.getForkliftDriverId());
        }
        return taskMapper.selectPage(req, query);
    }

    @Override
    public RdLoadingTaskDetailRespVO getTask(Long id, Long driverId) {
        RdLoadingTaskDO task = taskMapper.selectById(id);
        if (task == null || driverId != null && !driverId.equals(task.getForkliftDriverId())) {
            throw exception(LOADING_TASK_NOT_EXISTS);
        }
        List<RdLoadingTaskLineDO> lines = new ArrayList<>(taskLineMapper.selectListByTaskId(id));
        lines.sort(Comparator.comparing(RdLoadingTaskLineDO::getLineNo));
        List<RdLoadingExceptionDO> exceptions = new ArrayList<>(exceptionMapper.selectListByTaskId(id));
        exceptions.sort(Comparator.comparing(RdLoadingExceptionDO::getReportedTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return new RdLoadingTaskDetailRespVO(task, lines, exceptions);
    }

    private static boolean positive(Integer value) { return value != null && value > 0; }
    private static boolean positive(BigDecimal value) { return value != null && value.signum() > 0; }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static int zero(Integer value) { return value == null ? 0 : value; }
    private static BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private static int nextVersion(Integer version) { return version == null ? 1 : version + 1; }

    private RdLoadingTaskLineDO resolveLoadingExceptionLine(Long taskLineId,
                                                            String packageCode,
                                                            List<RdLoadingTaskLineDO> lines) {
        RdLoadingTaskLineDO matched = null;
        if (taskLineId != null) {
            matched = lines.stream()
                    .filter(item -> Objects.equals(item.getId(), taskLineId))
                    .findFirst().orElse(null);
            if (matched == null) {
                throw exception(LOADING_PACKAGE_NOT_IN_TASK);
            }
        }
        String normalizedPackageCode = normalizePackageCode(packageCode);
        if (normalizedPackageCode != null) {
            RdLoadingTaskLineDO codeMatched = lines.stream()
                    .filter(item -> Objects.equals(normalizeCode(item.getPackageCode()),
                            normalizeCode(normalizedPackageCode)))
                    .findFirst().orElse(null);
            if (codeMatched == null || matched != null && !Objects.equals(matched.getId(), codeMatched.getId())) {
                throw exception(LOADING_PACKAGE_NOT_IN_TASK);
            }
            matched = codeMatched;
        }
        return matched;
    }

    private void closeLoadingExceptions(RdLoadingTaskDO task, List<RdLoadingTaskLineDO> lines,
                                        List<RdLoadingExceptionDO> openExceptions,
                                        RdLoadingExceptionResolveReqVO req,
                                        Long keeperId,
                                        String keeperName,
                                        LocalDateTime now) {
        for (RdLoadingExceptionDO item : openExceptions) {
            item.setExceptionStatus(EXCEPTION_STATUS_RESOLVED);
            item.setHandlerId(keeperId);
            item.setHandlerName(keeperName);
            item.setHandleTime(now);
            item.setHandlingResult(req.getHandlingResult());
            item.setResolutionAction(req.getResolutionAction());
            item.setResolvedLocationId(req.getResolvedLocationId());
            item.setResolvedLocationCode(blankToNull(req.getResolvedLocationCode()));
            item.setVersion(nextVersion(item.getVersion()));
            exceptionMapper.updateById(item);
            for (RdLoadingTaskLineDO line : lines) {
                if (!LINE_CANCELLED.equals(line.getLineStatus()) && !LINE_OUTBOUNDED.equals(line.getLineStatus())
                        && (item.getPackageId() == null || Objects.equals(item.getPackageId(), line.getPackageId()))) {
                    taskHistoryService.append(taskEvent(task, line, "EXCEPTION_RESOLVED", now,
                            keeperId, keeperName).setStage("EXCEPTION").setSourceType("LOADING_EXCEPTION")
                            .setSourceId(item.getId()).setSourceNo(item.getExceptionNo())
                            .setSourceVersion(item.getVersion()).setDetail("装车任务 " + task.getTaskNo()
                                    + " / " + req.getResolutionAction() + " / " + req.getHandlingResult()));
                }
            }
        }
    }

    private void replaceLoadingTaskPackages(RdLoadingTaskDO task,
                                            List<RdLoadingTaskLineDO> lines,
                                            List<RdLoadingExceptionResolveReqVO.Replacement> replacements,
                                            LocalDateTime now, Long keeperId, String keeperName) {
        if (replacements == null || replacements.isEmpty()) {
            throw exception(LOADING_REPLACEMENT_REQUIRED);
        }
        Map<Long, RdLoadingTaskLineDO> lineMap = lines.stream()
                .collect(Collectors.toMap(RdLoadingTaskLineDO::getId, item -> item));
        Set<Long> replacedLineIds = new HashSet<>();
        Set<String> replacementCodes = new HashSet<>();
        for (RdLoadingExceptionResolveReqVO.Replacement replacement : replacements) {
            String packageCode = normalizePackageCode(replacement == null ? null : replacement.getNewPackageCode());
            if (replacement == null || replacement.getTaskLineId() == null
                    || packageCode == null || !replacementCodes.add(normalizeCode(packageCode))) {
                throw exception(LOADING_REPLACEMENT_REQUIRED);
            }
            RdLoadingTaskLineDO line = lineMap.get(replacement.getTaskLineId());
            if (line == null || !replacedLineIds.add(line.getId())) {
                throw exception(LOADING_PACKAGE_NOT_IN_TASK);
            }
            if (!LINE_PLANNED.equals(line.getLineStatus()) && !LINE_EXCEPTION.equals(line.getLineStatus())
                    || line.getOutboundOrderId() != null || line.getStockTransactionId() != null) {
                throw exception(LOADING_REPLACEMENT_LINE_INVALID);
            }
            if (Objects.equals(normalizeCode(line.getPackageCode()), normalizeCode(packageCode))) {
                throw exception(LOADING_PACKAGE_INVALID);
            }
            RdPackageUnitDO unit = packageMapper.selectByPackageCodeForUpdate(packageCode);
            RdOrderFulfillmentDO fulfillment = validateReplacementPackage(task, line, unit);
            RdLoadingPackageClaimDO newClaim = claimMapper.selectByPackageIdForUpdate(unit.getId());
            if (newClaim != null) {
                throw exception(LOADING_PACKAGE_CLAIMED);
            }
            RdLoadingPackageClaimDO oldClaim = claimMapper.selectByPackageIdForUpdate(line.getPackageId());
            if (oldClaim == null || !Objects.equals(oldClaim.getTaskId(), task.getId())
                    || !Objects.equals(oldClaim.getTaskLineId(), line.getId())) {
                throw exception(LOADING_PACKAGE_INVALID);
            }
            claimMapper.deleteById(oldClaim.getId());
            String previousCode = line.getPackageCode();
            taskHistoryService.append(taskEvent(task, line, "REPLACED_OUT", now, keeperId, keeperName)
                    .setSourceVersion(nextVersion(task.getVersion())).setRelatedPackageCode(unit.getPackageCode()));
            applyReplacementPackage(line, unit, fulfillment);
            taskLineMapper.updateById(line);
            taskHistoryService.append(taskEvent(task, line, "REPLACED_IN", now, keeperId, keeperName)
                    .setSourceVersion(nextVersion(task.getVersion())).setRelatedPackageCode(previousCode));
            RdLoadingPackageClaimDO claim = new RdLoadingPackageClaimDO();
            Long tenantId = task.getTenantId() == null ? TenantContextHolder.getTenantId() : task.getTenantId();
            claim.setTenantId(tenantId);
            claim.setPackageId(unit.getId());
            claim.setTaskId(task.getId());
            claim.setTaskLineId(line.getId());
            claim.setClaimedTime(now);
            try {
                claimMapper.insert(claim);
            } catch (DuplicateKeyException ex) {
                throw exception(LOADING_PACKAGE_CLAIMED);
            }
        }
        recalculateLoadingPlan(task, lines);
        returnLoadingTaskToDriver(task, lines, now);
    }

    private RdOrderFulfillmentDO validateReplacementPackage(RdLoadingTaskDO task,
                                                            RdLoadingTaskLineDO line,
                                                            RdPackageUnitDO unit) {
        Long tenantId = task.getTenantId() == null ? TenantContextHolder.getTenantId() : task.getTenantId();
        if (unit == null || tenantId != null && !Objects.equals(unit.getTenantId(), tenantId)
                || !RdPackageStatusEnum.IN_STOCK.getStatus().equals(unit.getStatus())
                || unit.getLocationId() == null || unit.getStockLotId() == null
                || !positive(unit.getPackageWeight())
                || !Objects.equals(unit.getCustomerName(), line.getCustomerName())
                || !Objects.equals(unit.getProductModel(), line.getProductModel())
                || !Objects.equals(unit.getFurnaceNo(), line.getFurnaceNo())
                || !Objects.equals(unit.getFurnaceLayer(), line.getFurnaceLayer())) {
            throw exception(LOADING_PACKAGE_INVALID);
        }
        RdStockLotDO lot = stockLotMapper.selectById(unit.getStockLotId());
        RdWarehouseLocationDO location = locationMapper.selectById(unit.getLocationId());
        if (lot == null || location == null || !Boolean.TRUE.equals(location.getEnabled())
                || Boolean.TRUE.equals(location.getFrozen())
                || !Objects.equals(location.getLocationCode(), unit.getLocationCode())
                || !Objects.equals(lot.getLocationId(), unit.getLocationId())
                || !Objects.equals(lot.getCustomerName(), unit.getCustomerName())
                || !Objects.equals(lot.getProductModel(), unit.getProductModel())
                || !Objects.equals(lot.getFurnaceNo(), unit.getFurnaceNo())
                || !Objects.equals(lot.getFurnaceLayer(), unit.getFurnaceLayer())) {
            throw exception(LOADING_PACKAGE_INVALID);
        }
        if (line.getSaleOrderId() == null) {
            return null;
        }
        RdSaleOrderDO order = saleOrderMapper.selectById(line.getSaleOrderId());
        if (order == null || !Objects.equals(order.getCustomerName(), task.getCustomerName())
                || !Objects.equals(order.getProductModel(), unit.getProductModel())
                || RdOrderStatusEnum.CLOSED.getStatus().equals(order.getStatus())
                || RdOrderStatusEnum.CANCELLED.getStatus().equals(order.getStatus())) {
            throw exception(PICKUP_SLIP_ORDER_MISMATCH);
        }
        RdOrderFulfillmentDO fulfillment = fulfillmentMapper.selectActive(order.getId(), unit.getStockLotId());
        if (fulfillment == null || !RdOrderFulfillmentStatusEnum.ALLOCATED.getStatus().equals(fulfillment.getStatus())) {
            throw exception(LOADING_FULFILLMENT_INSUFFICIENT);
        }
        BigDecimal remaining = zero(fulfillment.getAllocatedQty()).subtract(zero(fulfillment.getOutboundQty()));
        if (remaining.compareTo(BigDecimal.ONE) < 0) {
            throw exception(LOADING_FULFILLMENT_INSUFFICIENT);
        }
        return fulfillment;
    }

    private void applyReplacementPackage(RdLoadingTaskLineDO line,
                                         RdPackageUnitDO unit,
                                         RdOrderFulfillmentDO fulfillment) {
        line.setPackageId(unit.getId());
        line.setPackageCode(unit.getPackageCode());
        line.setFulfillmentId(fulfillment == null ? null : fulfillment.getId());
        line.setStockLotId(unit.getStockLotId());
        line.setPlannedLocationId(unit.getLocationId());
        line.setPlannedLocationCode(unit.getLocationCode());
        line.setCustomerName(unit.getCustomerName());
        line.setProductModel(unit.getProductModel());
        line.setFurnaceNo(unit.getFurnaceNo());
        line.setFurnaceLayer(unit.getFurnaceLayer());
        line.setPackageWeight(unit.getPackageWeight());
        line.setLineStatus(LINE_PLANNED);
        line.setScanTime(null);
        line.setPlanVersion(nextVersion(line.getPlanVersion()));
        line.setVersion(nextVersion(line.getVersion()));
        line.setRemark(joinRemark("仓管异常换货", line.getRemark()));
    }

    private void recalculateLoadingPlan(RdLoadingTaskDO task, List<RdLoadingTaskLineDO> lines) {
        List<RdLoadingTaskLineDO> activeLines = lines.stream()
                .filter(item -> !LINE_CANCELLED.equals(item.getLineStatus()))
                .collect(Collectors.toList());
        task.setPlannedPackageCount(activeLines.size());
        task.setPlannedWeight(activeLines.stream()
                .map(item -> zero(item.getPackageWeight()))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private void returnLoadingTaskToDriver(RdLoadingTaskDO task,
                                           List<RdLoadingTaskLineDO> lines,
                                           LocalDateTime now) {
        for (RdLoadingTaskLineDO line : lines) {
            if (LINE_EXCEPTION.equals(line.getLineStatus())) {
                line.setLineStatus(LINE_PLANNED);
                line.setVersion(nextVersion(line.getVersion()));
                taskLineMapper.updateById(line);
            }
        }
        updateLoadingTaskProgress(task, lines);
        task.setStatus(RdLoadingTaskStatusMachine.transition(RdLoadingTaskStatusEnum.EXCEPTION_PENDING,
                RdLoadingTaskActionEnum.REASSIGN, RdLoadingTaskActorEnum.WAREHOUSE_KEEPER).getStatus());
        if (zero(task.getScannedPackageCount()) < zero(task.getPlannedPackageCount())) {
            task.setScanCompleteTime(null);
        }
        if (task.getStartTime() == null) {
            task.setStartTime(now);
        }
        task.setPlanVersion(nextVersion(task.getPlanVersion()));
        task.setVersion(nextVersion(task.getVersion()));
        taskMapper.updateById(task);
    }

    private void cancelLoadingTask(RdLoadingTaskDO task,
                                   List<RdLoadingTaskLineDO> lines,
                                   String cancelReason,
                                   LocalDateTime now) {
        for (RdLoadingTaskLineDO line : lines) {
            if (!LINE_OUTBOUNDED.equals(line.getLineStatus())) {
                line.setLineStatus(LINE_CANCELLED);
                line.setVersion(nextVersion(line.getVersion()));
                taskLineMapper.updateById(line);
            }
        }
        for (RdLoadingPackageClaimDO claim : claimMapper.selectListByTaskIdForUpdate(task.getId())) {
            claimMapper.deleteById(claim.getId());
        }
        task.setStatus(RdLoadingTaskStatusMachine.transition(RdLoadingTaskStatusEnum.EXCEPTION_PENDING,
                RdLoadingTaskActionEnum.CANCEL, RdLoadingTaskActorEnum.WAREHOUSE_KEEPER).getStatus());
        task.setCancelReason(cancelReason);
        task.setCancelTime(now);
        task.setVersion(nextVersion(task.getVersion()));
        taskMapper.updateById(task);
        RdPickupSlipDO slip = slipMapper.selectByIdForUpdate(task.getPickupSlipId(), TenantContextHolder.getTenantId());
        if (slip == null) {
            throw exception(PICKUP_SLIP_NOT_EXISTS);
        }
        slip.setStatus("ISSUED");
        slip.setVersion(nextVersion(slip.getVersion()));
        slipMapper.updateById(slip);
    }

    private void updateLoadingTaskProgress(RdLoadingTaskDO task, List<RdLoadingTaskLineDO> lines) {
        int scanned = (int) lines.stream().filter(item -> countedAsScanned(item.getLineStatus())).count();
        BigDecimal scannedWeight = lines.stream()
                .filter(item -> countedAsScanned(item.getLineStatus()))
                .map(item -> zero(item.getPackageWeight()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        task.setScannedPackageCount(scanned);
        task.setScannedWeight(scannedWeight);
    }

    private void recordLoadingTaskReturn(RdLoadingTaskDO task, List<RdLoadingTaskLineDO> lines,
                                         LocalDateTime time, Long keeperId, String keeperName, String reason) {
        for (RdLoadingTaskLineDO line : lines) {
            if (!LINE_CANCELLED.equals(line.getLineStatus()) && !LINE_OUTBOUNDED.equals(line.getLineStatus())) {
                taskHistoryService.append(taskEvent(task, line, "RETURNED", time, keeperId, keeperName)
                        .setDetail(reason));
            }
        }
    }

    private RdPackageTaskEventDO taskEvent(RdLoadingTaskDO task, RdLoadingTaskLineDO line,
                                           String eventCode, LocalDateTime time,
                                           Long operatorId, String operatorName) {
        return new RdPackageTaskEventDO().setPackageId(line.getPackageId()).setPackageCode(line.getPackageCode())
                .setStage("LOADING").setEventCode(eventCode).setSourceType("LOADING_TASK")
                .setSourceId(task.getId()).setSourceNo(task.getTaskNo()).setSourceVersion(zero(task.getVersion()))
                .setOperatorId(operatorId).setOperatorName(operatorName).setEventTime(time)
                .setLocationCode(line.getPlannedLocationCode());
    }

    private RdLoadingTaskLineDO findLoadingLineForPackage(Long taskId, String packageCode) {
        if (taskId != null) {
            return taskLineMapper.selectListByTaskId(taskId).stream()
                    .filter(item -> Objects.equals(normalizeCode(item.getPackageCode()), normalizeCode(packageCode)))
                    .findFirst().orElse(null);
        }
        return taskLineMapper.selectActiveByPackageCode(packageCode);
    }

    private List<RdLoadingTaskLineDO> sortedTaskLines(Long taskId) {
        List<RdLoadingTaskLineDO> lines = new ArrayList<>(taskLineMapper.selectListByTaskId(taskId));
        lines.sort(Comparator.comparing(RdLoadingTaskLineDO::getLineNo));
        return lines;
    }

    private RdLoadingScanRecordDO insertScanRecord(RdLoadingBatchScanConfirmReqVO req,
                                                   RdLoadingTaskDO task,
                                                   RdLoadingTaskLineDO line,
                                                   String packageCode,
                                                   String scanResult,
                                                   boolean success,
                                                   String statusBefore,
                                                   String statusAfter,
                                                   Long operatorId,
                                                   String operatorName,
                                                   LocalDateTime now) {
        RdLoadingScanRecordDO record = new RdLoadingScanRecordDO();
        record.setTaskId(task.getId());
        record.setTaskLineId(line == null ? null : line.getId());
        record.setPackageId(line == null ? null : line.getPackageId());
        record.setPackageCode(packageCode);
        record.setPlanVersion(task.getPlanVersion());
        record.setIdempotencyKey(req.getIdempotencyKey());
        record.setScanResult(scanResult);
        record.setSuccess(success);
        record.setTaskStatusBefore(statusBefore);
        record.setTaskStatusAfter(statusAfter);
        record.setObservedLocationCode(line == null ? null : line.getPlannedLocationCode());
        record.setDeviceCode(req.getDeviceCode());
        record.setOperatorId(operatorId);
        record.setOperatorName(operatorName);
        record.setDeviceTime(req.getDeviceTime());
        record.setScanTime(now);
        scanRecordMapper.insert(record);
        return record;
    }

    private RdLoadingBatchScanRespVO buildBatchScanResponse(RdLoadingScanRecordDO record,
                                                            RdLoadingTaskDO task,
                                                            List<RdLoadingTaskLineDO> lines,
                                                            RdLoadingTaskLineDO matched,
                                                            String packageCode,
                                                            String scanResult,
                                                            boolean success,
                                                            boolean replayed,
                                                            int confirmedCount,
                                                            BigDecimal confirmedWeight,
                                                            String message) {
        RdLoadingBatchScanRespVO resp = new RdLoadingBatchScanRespVO();
        resp.setRecordId(record == null ? null : record.getId());
        resp.setTask(task);
        resp.setLines(lines);
        resp.setMatchedTaskLineId(matched == null ? null : matched.getId());
        resp.setRepresentativePackageCode(packageCode);
        resp.setScanResult(scanResult);
        resp.setSuccess(success);
        resp.setReplayed(replayed);
        resp.setConfirmedCount(confirmedCount);
        resp.setRemainingCount((int) lines.stream().filter(item -> !countedAsScanned(item.getLineStatus())).count());
        resp.setConfirmedWeight(confirmedWeight);
        resp.setMessage(message);
        return resp;
    }

    private RdOutboundOrderDO buildLoadingOutboundOrder(RdLoadingTaskDO task,
                                                        RdOrderFulfillmentDO fulfillment,
                                                        RdStockLotDO stockLot,
                                                        List<RdLoadingTaskLineDO> lines,
                                                        Long keeperId,
                                                        String keeperName,
                                                        LocalDateTime now,
                                                        int orderIndex,
                                                        String remark) {
        BigDecimal weight = lines.stream()
                .map(item -> zero(item.getPackageWeight()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        RdOutboundOrderDO outboundOrder = new RdOutboundOrderDO();
        outboundOrder.setOutboundNo(generateLoadingOutboundNo(task.getTaskNo(), orderIndex));
        outboundOrder.setOutboundDate(LocalDate.now());
        outboundOrder.setOrderId(fulfillment.getOrderId());
        outboundOrder.setFulfillmentId(fulfillment.getId());
        outboundOrder.setStockLotId(fulfillment.getStockLotId());
        outboundOrder.setQuantity(BigDecimal.valueOf(lines.size()));
        outboundOrder.setPackageWeight(stockLot.getPackageWeight());
        outboundOrder.setWeight(weight);
        outboundOrder.setStatus(RdOutboundOrderStatusEnum.APPROVED.getStatus());
        outboundOrder.setVersion(0);
        outboundOrder.setSubmitterName("装车任务自动生成");
        outboundOrder.setSubmitTime(now);
        outboundOrder.setReviewerId(keeperId);
        outboundOrder.setReviewerName(keeperName);
        outboundOrder.setReviewTime(now);
        outboundOrder.setRemark(joinRemark("来源装车任务 " + task.getTaskNo(), remark));
        return outboundOrder;
    }

    private RdOutboundOrderLineDO buildLoadingOutboundLine(RdOutboundOrderDO order,
                                                           RdLoadingTaskLineDO taskLine,
                                                           RdPackageUnitDO unit,
                                                           Long keeperId,
                                                           String keeperName) {
        RdOutboundOrderLineDO outboundLine = new RdOutboundOrderLineDO();
        outboundLine.setOrderId(order.getId());
        outboundLine.setPackageId(unit.getId());
        outboundLine.setPackageCode(unit.getPackageCode());
        outboundLine.setStockLotId(unit.getStockLotId());
        outboundLine.setLocationId(unit.getLocationId());
        outboundLine.setLocationCode(unit.getLocationCode());
        outboundLine.setCustomerName(unit.getCustomerName());
        outboundLine.setProductModel(unit.getProductModel());
        outboundLine.setFurnaceNo(unit.getFurnaceNo());
        outboundLine.setFurnaceLayer(unit.getFurnaceLayer());
        outboundLine.setPackageWeight(unit.getPackageWeight());
        outboundLine.setScanDevice(SCAN_DEVICE_LOADING_TASK);
        outboundLine.setScannerId(keeperId);
        outboundLine.setScannerName(keeperName);
        return outboundLine;
    }

    private void validateLoadingLineForOutbound(RdLoadingTaskDO task,
                                                RdLoadingTaskLineDO line,
                                                RdPackageUnitDO unit,
                                                RdOrderFulfillmentDO fulfillment) {
        if (unit == null || !Objects.equals(unit.getTenantId(), task.getTenantId())
                || !RdPackageStatusEnum.IN_STOCK.getStatus().equals(unit.getStatus())
                || !Objects.equals(unit.getId(), line.getPackageId())
                || !Objects.equals(unit.getPackageCode(), line.getPackageCode())
                || !Objects.equals(unit.getStockLotId(), line.getStockLotId())
                || !Objects.equals(unit.getStockLotId(), fulfillment.getStockLotId())
                || !Objects.equals(line.getSaleOrderId(), fulfillment.getOrderId())
                || !Objects.equals(unit.getLocationId(), line.getPlannedLocationId())
                || !Objects.equals(unit.getLocationCode(), line.getPlannedLocationCode())
                || !Objects.equals(unit.getCustomerName(), line.getCustomerName())
                || !Objects.equals(unit.getProductModel(), line.getProductModel())
                || !Objects.equals(unit.getFurnaceNo(), line.getFurnaceNo())
                || !Objects.equals(unit.getFurnaceLayer(), line.getFurnaceLayer())
                || unit.getPackageWeight() == null
                || unit.getPackageWeight().compareTo(zero(line.getPackageWeight())) != 0) {
            throw exception(LOADING_PACKAGE_INVALID);
        }
    }

    private void reconcileLocationOccupancy(Set<Long> locationIds) {
        for (Long locationId : locationIds) {
            if (locationId == null) {
                continue;
            }
            RdWarehouseLocationDO location = locationMapper.selectByIdForUpdate(locationId);
            if (location == null) {
                continue;
            }
            RdLocationCapacitySummaryDO summary = packageMapper
                    .selectLocationOccupancySummaryByLocationId(locationId, PHYSICAL_STOCK_STATUSES);
            location.setOccupiedPackages(summary == null ? BigDecimal.ZERO : zero(summary.getPackageCount()));
            location.setOccupiedWeight(summary == null ? BigDecimal.ZERO : zero(summary.getTotalWeight()));
            location.setVersion(nextVersion(location.getVersion()));
            locationMapper.updateById(location);
        }
    }

    private static RdLoadingTaskStatusEnum parseLoadingTaskStatus(String status) {
        try {
            return RdLoadingTaskStatusEnum.valueOf(status);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw exception(LOADING_TASK_STATUS_INVALID);
        }
    }

    private static boolean countedAsScanned(String lineStatus) {
        return LINE_SCANNED.equals(lineStatus) || "LOADED".equals(lineStatus) || "OUTBOUNDED".equals(lineStatus);
    }

    private static String normalizePackageCode(String value) {
        String text = blankToNull(value);
        if (text == null) {
            return null;
        }
        int query = text.indexOf('?');
        if (query >= 0) {
            text = text.substring(0, query);
        }
        int hash = text.indexOf('#');
        if (hash >= 0) {
            text = text.substring(0, hash);
        }
        int slash = text.lastIndexOf('/');
        if (slash >= 0 && slash < text.length() - 1) {
            text = text.substring(slash + 1);
        }
        return text.trim();
    }

    private static String normalizeCode(String value) {
        return value == null ? null : value.trim();
    }

    private static String generateLoadingOutboundNo(String taskNo, int orderIndex) {
        return "CK" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + "-" + orderIndex + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private static String generateLoadingExceptionNo() {
        return "LDE" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    private static String joinRemark(String prefix, String remark) {
        String text = blankToNull(remark);
        return text == null ? prefix : prefix + "；" + text;
    }
}
