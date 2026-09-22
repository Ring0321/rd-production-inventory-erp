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
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockLotDO;
import com.ruiding.erp.dal.mysql.loading.*;
import com.ruiding.erp.dal.mysql.masterdata.RdWarehouseLocationMapper;
import com.ruiding.erp.dal.mysql.order.RdOrderFulfillmentMapper;
import com.ruiding.erp.dal.mysql.order.RdSaleOrderMapper;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.dal.mysql.stock.RdStockLotMapper;
import com.ruiding.erp.enums.RdLoadingTaskActionEnum;
import com.ruiding.erp.enums.RdLoadingTaskActorEnum;
import com.ruiding.erp.enums.RdLoadingTaskStatusEnum;
import com.ruiding.erp.enums.RdOrderFulfillmentStatusEnum;
import com.ruiding.erp.enums.RdOrderStatusEnum;
import com.ruiding.erp.enums.RdPackageStatusEnum;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    @Resource private RdPickupSlipMapper slipMapper;
    @Resource private RdPickupSlipLineMapper slipLineMapper;
    @Resource private RdLoadingTaskMapper taskMapper;
    @Resource private RdLoadingTaskLineMapper taskLineMapper;
    @Resource private RdLoadingPackageClaimMapper claimMapper;
    @Resource private RdPackageUnitMapper packageMapper;
    @Resource private RdStockLotMapper stockLotMapper;
    @Resource private RdWarehouseLocationMapper locationMapper;
    @Resource private RdSaleOrderMapper saleOrderMapper;
    @Resource private RdOrderFulfillmentMapper fulfillmentMapper;
    @Resource private AdminUserService userService;
    @Resource private PermissionService permissionService;

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
            line.setLineStatus("PLANNED");
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
        }
        slip.setStatus("DISPATCHED");
        slip.setVersion(slip.getVersion() == null ? 1 : slip.getVersion() + 1);
        slipMapper.updateById(slip);
        return task.getId();
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
        List<RdLoadingTaskLineDO> lines = taskLineMapper.selectListByTaskId(id);
        lines.sort(Comparator.comparing(RdLoadingTaskLineDO::getLineNo));
        return new RdLoadingTaskDetailRespVO(task, lines);
    }

    private static boolean positive(Integer value) { return value != null && value > 0; }
    private static boolean positive(BigDecimal value) { return value != null && value.signum() > 0; }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
