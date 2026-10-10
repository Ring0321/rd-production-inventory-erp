package com.ruiding.erp.service.putaway;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import com.mzt.logapi.context.LogRecordContext;
import com.mzt.logapi.starter.annotation.LogRecord;
import com.ruiding.erp.controller.admin.putaway.vo.RdPendingPutawayPackagePageReqVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayExceptionCreateReqVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayDriverOptionRespVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayLocationMapRespVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayScanReqVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayScanRespVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayBatchScanPreviewReqVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayBatchScanConfirmReqVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayBatchScanRespVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayTaskConfirmReqVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayTaskCreateReqVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayTaskDetailRespVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayTaskPageReqVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayTaskReassignReqVO;
import com.ruiding.erp.dal.dataobject.inbound.RdInboundOrderDO;
import com.ruiding.erp.dal.dataobject.inbound.RdInboundOrderLineDO;
import com.ruiding.erp.dal.dataobject.masterdata.RdWarehouseLocationDO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.production.RdProductionOutputDO;
import com.ruiding.erp.dal.dataobject.putaway.RdLocationCapacitySummaryDO;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayExceptionDO;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayScanRecordDO;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayBatchScanRecordDO;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayTaskDO;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayTaskLineDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockLotDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockTransactionDO;
import com.ruiding.erp.dal.mysql.inbound.RdInboundOrderLineMapper;
import com.ruiding.erp.dal.mysql.inbound.RdInboundOrderMapper;
import com.ruiding.erp.dal.mysql.masterdata.RdWarehouseLocationMapper;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.dal.mysql.putaway.RdPutawayExceptionMapper;
import com.ruiding.erp.dal.mysql.putaway.RdPutawayScanRecordMapper;
import com.ruiding.erp.dal.mysql.putaway.RdPutawayBatchScanRecordMapper;
import com.ruiding.erp.dal.mysql.putaway.RdPutawayTaskLineMapper;
import com.ruiding.erp.dal.mysql.putaway.RdPutawayTaskMapper;
import com.ruiding.erp.dal.mysql.production.RdProductionOutputMapper;
import com.ruiding.erp.dal.mysql.stock.RdStockLotMapper;
import com.ruiding.erp.dal.mysql.stock.RdStockTransactionMapper;
import com.ruiding.erp.enums.RdInboundOrderStatusEnum;
import com.ruiding.erp.enums.RdLogRecordConstants;
import com.ruiding.erp.enums.RdPackageStatusEnum;
import com.ruiding.erp.enums.RdProductionOutputStatusEnum;
import com.ruiding.erp.enums.RdPutawayTaskActionEnum;
import com.ruiding.erp.enums.RdPutawayTaskStatusEnum;
import com.ruiding.erp.enums.RdStockLotStatusEnum;
import com.ruiding.erp.enums.RdStockTransactionTypeEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static com.ruiding.erp.enums.ErrorCodeConstants.PUTAWAY_SCAN_KEY_CONFLICT;
import static com.ruiding.erp.enums.ErrorCodeConstants.PUTAWAY_BATCH_SELECTION_INVALID;
import static com.ruiding.erp.enums.ErrorCodeConstants.PUTAWAY_SCAN_PACKAGE_MISMATCH;
import static com.ruiding.erp.enums.ErrorCodeConstants.PUTAWAY_CONFIRM_HAS_OPEN_EXCEPTION;
import static com.ruiding.erp.enums.ErrorCodeConstants.PUTAWAY_CONFIRM_PACKAGE_INVALID;
import static com.ruiding.erp.enums.ErrorCodeConstants.PUTAWAY_CONFIRM_PACKAGES_INCOMPLETE;
import static com.ruiding.erp.enums.ErrorCodeConstants.PUTAWAY_DRIVER_INVALID;
import static com.ruiding.erp.enums.ErrorCodeConstants.PUTAWAY_EXCEPTION_TYPE_INVALID;
import static com.ruiding.erp.enums.ErrorCodeConstants.PUTAWAY_LOCATION_CAPACITY_INSUFFICIENT;
import static com.ruiding.erp.enums.ErrorCodeConstants.PUTAWAY_LOCATION_INVALID;
import static com.ruiding.erp.enums.ErrorCodeConstants.PUTAWAY_PACKAGE_DATA_INCOMPLETE;
import static com.ruiding.erp.enums.ErrorCodeConstants.PUTAWAY_PACKAGE_DUPLICATE;
import static com.ruiding.erp.enums.ErrorCodeConstants.PUTAWAY_PACKAGE_STATUS_INVALID;
import static com.ruiding.erp.enums.ErrorCodeConstants.PUTAWAY_REASSIGN_REASON_REQUIRED;
import static com.ruiding.erp.enums.ErrorCodeConstants.PUTAWAY_TASK_NOT_EXISTS;
import static com.ruiding.erp.enums.ErrorCodeConstants.PUTAWAY_TASK_NOT_ASSIGNED;
import static com.ruiding.erp.enums.ErrorCodeConstants.PUTAWAY_TASK_STATUS_INVALID;
import static com.ruiding.erp.enums.ErrorCodeConstants.PUTAWAY_TASK_VERSION_CONFLICT;

@Service
public class RdPutawayTaskServiceImpl implements RdPutawayTaskService {
    private static final String FORKLIFT_DRIVER_ROLE = "rd_forklift_driver";
    private static final String LINE_PENDING = "PENDING";
    private static final String LINE_SCANNED = "SCANNED";
    private static final String LINE_EXCEPTION = "EXCEPTION";
    private static final String LINE_CONFIRMED = "CONFIRMED";
    private static final Set<String> EXCEPTION_TYPES = Set.of(
            "LOCATION_CAPACITY_INSUFFICIENT", "LOCATION_OCCUPIED", "PACKAGE_MISMATCH",
            "LABEL_DAMAGED", "PACKAGE_MISSING", "OTHER");
    private static final Set<String> ACTIVE_TASK_STATUSES = Set.of(
            RdPutawayTaskStatusEnum.PENDING_PUTAWAY.getStatus(),
            RdPutawayTaskStatusEnum.ASSIGNED.getStatus(),
            RdPutawayTaskStatusEnum.MOVING.getStatus(),
            RdPutawayTaskStatusEnum.PENDING_CONFIRMATION.getStatus(),
            RdPutawayTaskStatusEnum.EXCEPTION_PENDING.getStatus());
    private static final Set<String> PHYSICAL_STOCK_STATUSES = Set.of(
            RdPackageStatusEnum.IN_STOCK.getStatus(),
            RdPackageStatusEnum.OUTBOUND_PENDING.getStatus());

    @Resource private RdPackageUnitMapper packageMapper;
    @Resource private RdWarehouseLocationMapper locationMapper;
    @Resource private RdPutawayTaskMapper taskMapper;
    @Resource private RdPutawayTaskLineMapper taskLineMapper;
    @Resource private RdPutawayExceptionMapper exceptionMapper;
    @Resource private RdPutawayScanRecordMapper scanRecordMapper;
    @Resource private RdPutawayBatchScanRecordMapper batchScanRecordMapper;
    @Resource private RdInboundOrderMapper inboundOrderMapper;
    @Resource private RdInboundOrderLineMapper inboundOrderLineMapper;
    @Resource private RdStockLotMapper stockLotMapper;
    @Resource private RdStockTransactionMapper stockTransactionMapper;
    @Resource private RdProductionOutputMapper productionOutputMapper;
    @Resource private AdminUserService adminUserService;
    @Resource private PermissionService permissionService;

    @Override
    public PageResult<RdPackageUnitDO> getPendingPackagePage(RdPendingPutawayPackagePageReqVO reqVO) {
        LambdaQueryWrapperX<RdPackageUnitDO> query = new LambdaQueryWrapperX<>();
        query.eq(RdPackageUnitDO::getStatus, RdPackageStatusEnum.QC_PASSED.getStatus());
        query.eqIfPresent(RdPackageUnitDO::getCustomerName, reqVO.getCustomerName());
        query.eqIfPresent(RdPackageUnitDO::getProductModel, reqVO.getProductModel());
        query.eqIfPresent(RdPackageUnitDO::getFurnaceNo, reqVO.getFurnaceNo());
        query.eqIfPresent(RdPackageUnitDO::getFurnaceLayer, reqVO.getFurnaceLayer());
        query.orderByAsc(RdPackageUnitDO::getProductionDate);
        query.orderByAsc(RdPackageUnitDO::getPackageCode);
        if (hasText(reqVO.getKeyword())) {
            query.and(item -> item.like(RdPackageUnitDO::getPackageCode, reqVO.getKeyword())
                    .or().like(RdPackageUnitDO::getCustomerName, reqVO.getKeyword())
                    .or().like(RdPackageUnitDO::getProductModel, reqVO.getKeyword())
                    .or().like(RdPackageUnitDO::getFurnaceNo, reqVO.getKeyword()));
        }
        return packageMapper.selectPage(reqVO, query);
    }

    @Override
    public List<RdPutawayLocationMapRespVO> getLocationMap(String warehouseCode, String areaCode) {
        List<RdWarehouseLocationDO> locations = locationMapper.selectList(
                new LambdaQueryWrapperX<RdWarehouseLocationDO>()
                        .eqIfPresent(RdWarehouseLocationDO::getWarehouseCode, warehouseCode)
                        .eqIfPresent(RdWarehouseLocationDO::getAreaCode, areaCode)
                        .orderByAsc(RdWarehouseLocationDO::getWarehouseCode)
                        .orderByAsc(RdWarehouseLocationDO::getAreaCode)
                        .orderByAsc(RdWarehouseLocationDO::getRowCode)
                        .orderByAsc(RdWarehouseLocationDO::getColumnCode)
                        .orderByAsc(RdWarehouseLocationDO::getLevelCode)
                        .orderByAsc(RdWarehouseLocationDO::getLocationCode));
        Map<Long, RdLocationCapacitySummaryDO> occupied = packageMapper
                .selectLocationOccupancySummary(PHYSICAL_STOCK_STATUSES).stream()
                .collect(Collectors.toMap(RdLocationCapacitySummaryDO::getLocationId, Function.identity()));
        Map<Long, RdLocationCapacitySummaryDO> reserved = taskMapper
                .selectLocationReservationSummary(ACTIVE_TASK_STATUSES).stream()
                .collect(Collectors.toMap(RdLocationCapacitySummaryDO::getLocationId, Function.identity()));
        return locations.stream().map(location -> toLocationMap(location,
                        occupied.get(location.getId()), reserved.get(location.getId())))
                .collect(Collectors.toList());
    }

    @Override
    public List<RdPutawayDriverOptionRespVO> getDriverOptions() {
        return adminUserService.getUserListByStatus(CommonStatusEnum.ENABLE.getStatus()).stream()
                .filter(user -> permissionService.hasAnyRoles(user.getId(), FORKLIFT_DRIVER_ROLE))
                .sorted(Comparator.comparing(AdminUserDO::getNickname,
                        Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(user -> new RdPutawayDriverOptionRespVO(user.getId(), user.getNickname(), user.getUsername()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = RdLogRecordConstants.PUTAWAY_TYPE,
            subType = RdLogRecordConstants.PUTAWAY_CREATE_SUB_TYPE,
            bizNo = RdLogRecordConstants.BIZ_ID_EXPRESSION,
            success = RdLogRecordConstants.PUTAWAY_CREATE_SUCCESS)
    public Long createTask(RdPutawayTaskCreateReqVO reqVO, Long warehouseKeeperId,
                           String warehouseKeeperName) {
        AdminUserDO driver = requireForkliftDriver(reqVO.getForkliftDriverId());
        LinkedHashSet<Long> packageIds = new LinkedHashSet<>(reqVO.getPackageIds());
        if (packageIds.size() != reqVO.getPackageIds().size()) {
            throw exception(PUTAWAY_PACKAGE_DUPLICATE);
        }
        RdWarehouseLocationDO location = requireAvailableLocationForUpdate(reqVO.getPlannedLocationId());
        List<RdPackageUnitDO> packages = new ArrayList<>();
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (Long packageId : packageIds.stream().sorted().collect(Collectors.toList())) {
            RdPackageUnitDO packageUnit = packageMapper.selectByIdForUpdate(packageId);
            validatePendingPackage(packageUnit);
            packages.add(packageUnit);
            totalWeight = totalWeight.add(packageUnit.getPackageWeight());
        }
        reconcileLocationCounters(location);
        BigDecimal packageCount = BigDecimal.valueOf(packages.size());
        requireCapacity(location, packageCount, totalWeight, BigDecimal.ZERO, BigDecimal.ZERO);

        RdPutawayTaskStatusEnum assigned = RdPutawayTaskStatusMachine.transition(
                RdPutawayTaskStatusEnum.PENDING_PUTAWAY, RdPutawayTaskActionEnum.ASSIGN);
        RdPutawayTaskDO task = new RdPutawayTaskDO()
                .setTaskNo(generateTaskNo())
                .setStatus(assigned.getStatus())
                .setPlannedLocationId(location.getId())
                .setPlannedLocationCode(location.getLocationCode())
                .setPackageCount(packages.size())
                .setScannedPackageCount(0)
                .setExceptionPackageCount(0)
                .setTotalWeight(totalWeight)
                .setWarehouseKeeperId(warehouseKeeperId)
                .setWarehouseKeeperName(warehouseKeeperName)
                .setForkliftDriverId(driver.getId())
                .setForkliftDriverName(displayName(driver))
                .setAssignedTime(LocalDateTime.now())
                .setVersion(0)
                .setRemark(reqVO.getRemark());
        taskMapper.insert(task);

        int lineNo = 1;
        for (RdPackageUnitDO packageUnit : packages) {
            taskLineMapper.insert(new RdPutawayTaskLineDO()
                    .setTaskId(task.getId())
                    .setLineNo(lineNo++)
                    .setPackageId(packageUnit.getId())
                    .setPackageCode(packageUnit.getPackageCode())
                    .setLineStatus("PENDING")
                    .setCustomerName(packageUnit.getCustomerName())
                    .setProductModel(packageUnit.getProductModel())
                    .setFurnaceNo(packageUnit.getFurnaceNo())
                    .setFurnaceLayer(packageUnit.getFurnaceLayer())
                    .setPackageWeight(packageUnit.getPackageWeight())
                    .setScanCount(0)
                    .setVersion(0));
            packageUnit.setStatus(RdPackageStatusEnum.INBOUND_PENDING.getStatus())
                    .setLocationId(null)
                    .setLocationCode(null)
                    .setVersion(nextVersion(packageUnit.getVersion()));
            packageMapper.updateById(packageUnit);
        }
        reserveLocation(location, packageCount, totalWeight);
        LogRecordContext.putVariable(RdLogRecordConstants.BIZ_ID_VARIABLE, task.getId());
        return task.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = RdLogRecordConstants.PUTAWAY_TYPE,
            subType = RdLogRecordConstants.PUTAWAY_REASSIGN_SUB_TYPE,
            bizNo = "{{#reqVO.id}}", success = RdLogRecordConstants.PUTAWAY_REASSIGN_SUCCESS)
    public void reassignTask(RdPutawayTaskReassignReqVO reqVO, Long warehouseKeeperId,
                             String warehouseKeeperName) {
        if (!hasText(reqVO.getHandlingResult())) {
            throw exception(PUTAWAY_REASSIGN_REASON_REQUIRED);
        }
        AdminUserDO driver = requireForkliftDriver(reqVO.getForkliftDriverId());
        RdPutawayTaskDO task = taskMapper.selectByIdForUpdate(reqVO.getId());
        if (task == null) {
            throw exception(PUTAWAY_TASK_NOT_EXISTS);
        }
        if (!reqVO.getVersion().equals(task.getVersion())) {
            throw exception(PUTAWAY_TASK_VERSION_CONFLICT);
        }
        RdPutawayTaskStatusEnum current = RdPutawayTaskStatusEnum.valueOf(task.getStatus());
        if (current != RdPutawayTaskStatusEnum.ASSIGNED
                && current != RdPutawayTaskStatusEnum.EXCEPTION_PENDING) {
            throw exception(PUTAWAY_TASK_STATUS_INVALID);
        }

        Map<Long, RdWarehouseLocationDO> lockedLocations = lockLocations(
                task.getPlannedLocationId(), reqVO.getPlannedLocationId());
        lockedLocations.values().forEach(this::reconcileLocationCounters);
        RdWarehouseLocationDO oldLocation = lockedLocations.get(task.getPlannedLocationId());
        RdWarehouseLocationDO newLocation = lockedLocations.get(reqVO.getPlannedLocationId());
        requireAvailableLocation(newLocation);
        BigDecimal packageCount = BigDecimal.valueOf(task.getPackageCount());
        BigDecimal totalWeight = zero(task.getTotalWeight());
        boolean sameLocation = task.getPlannedLocationId().equals(reqVO.getPlannedLocationId());
        requireCapacity(newLocation, packageCount, totalWeight,
                sameLocation ? packageCount : BigDecimal.ZERO,
                sameLocation ? totalWeight : BigDecimal.ZERO);
        if (!sameLocation) {
            releaseLocation(oldLocation, packageCount, totalWeight);
            reserveLocation(newLocation, packageCount, totalWeight);
        }

        Long previousLocationId = task.getPlannedLocationId();
        String previousLocationCode = task.getPlannedLocationCode();
        if (current == RdPutawayTaskStatusEnum.EXCEPTION_PENDING) {
            task.setStatus(RdPutawayTaskStatusMachine.transition(current,
                    RdPutawayTaskActionEnum.REASSIGN).getStatus());
            for (RdPutawayExceptionDO item : exceptionMapper.selectOpenListByTaskIdForUpdate(task.getId())) {
                item.setExceptionStatus("RESOLVED")
                        .setHandlerId(warehouseKeeperId)
                        .setHandlerName(warehouseKeeperName)
                        .setHandleTime(LocalDateTime.now())
                        .setHandlingResult(reqVO.getHandlingResult())
                        .setResolutionAction("REASSIGN")
                        .setPreviousLocationId(previousLocationId)
                        .setPreviousLocationCode(previousLocationCode)
                        .setResolvedLocationId(newLocation.getId())
                        .setResolvedLocationCode(newLocation.getLocationCode())
                        .setVersion(nextVersion(item.getVersion()));
                exceptionMapper.updateById(item);
            }
            for (RdPutawayTaskLineDO line : taskLineMapper.selectListByTaskIdForUpdate(task.getId())) {
                if (LINE_EXCEPTION.equals(line.getLineStatus()) && zero(line.getScanCount()) == 0) {
                    line.setLineStatus(LINE_PENDING).setVersion(nextVersion(line.getVersion()));
                    taskLineMapper.updateById(line);
                }
            }
            task.setExceptionPackageCount(0);
        }
        task.setPlannedLocationId(newLocation.getId())
                .setPlannedLocationCode(newLocation.getLocationCode())
                .setForkliftDriverId(driver.getId())
                .setForkliftDriverName(displayName(driver))
                .setWarehouseKeeperId(warehouseKeeperId)
                .setWarehouseKeeperName(warehouseKeeperName)
                .setAssignedTime(LocalDateTime.now())
                .setVersion(nextVersion(task.getVersion()))
                .setRemark(appendRemark(task.getRemark(), reqVO.getHandlingResult()));
        taskMapper.updateById(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = RdLogRecordConstants.PUTAWAY_TYPE,
            subType = RdLogRecordConstants.PUTAWAY_SCAN_SUB_TYPE,
            bizNo = "{{#reqVO.id}}", success = RdLogRecordConstants.PUTAWAY_SCAN_SUCCESS)
    public RdPutawayScanRespVO scanPackage(RdPutawayScanReqVO reqVO, Long warehouseKeeperId,
                                           String warehouseKeeperName) {
        if (!permissionService.hasAnyPermissions(warehouseKeeperId, "rd:putaway-task:confirm")) {
            throw exception(FORBIDDEN);
        }
        RdPutawayTaskDO task = taskMapper.selectByIdForUpdate(reqVO.getId());
        if (task == null) {
            throw exception(PUTAWAY_TASK_NOT_EXISTS);
        }
        RdPutawayScanRecordDO replay = scanRecordMapper.selectByTaskAndIdempotencyKey(
                task.getId(), reqVO.getIdempotencyKey());
        if (replay != null) {
            if (!Objects.equals(replay.getOperatorId(), warehouseKeeperId)
                    || !Objects.equals(normalizeCode(replay.getPackageCode()),
                    normalizeCode(normalizePackageCode(reqVO.getPackageCode())))) {
                throw exception(PUTAWAY_SCAN_KEY_CONFLICT);
            }
            return toScanResponse(replay, task, true);
        }

        String packageCode = normalizePackageCode(reqVO.getPackageCode());
        String statusBefore = task.getStatus();
        List<RdPutawayTaskLineDO> lines = taskLineMapper.selectListByTaskIdForUpdate(task.getId());
        RdPutawayTaskLineDO line = lines.stream()
                .filter(item -> Objects.equals(normalizeCode(item.getPackageCode()), normalizeCode(packageCode)))
                .findFirst().orElse(null);
        if (line == null) {
            RdPutawayScanRecordDO record = insertScanRecord(reqVO, task, null, packageCode,
                    "MISMATCH", false, "PACKAGE_MISMATCH", "该包码不属于当前上架任务",
                    statusBefore, statusBefore, warehouseKeeperId, warehouseKeeperName);
            return toScanResponse(record, task, false);
        }
        if (zero(line.getScanCount()) > 0 || LINE_SCANNED.equals(line.getLineStatus())
                || LINE_CONFIRMED.equals(line.getLineStatus())) {
            RdPutawayScanRecordDO record = insertScanRecord(reqVO, task, line, packageCode,
                    "DUPLICATE", false, "PACKAGE_ALREADY_SCANNED", "该包码已经扫码，不重复计数",
                    statusBefore, statusBefore, warehouseKeeperId, warehouseKeeperName);
            return toScanResponse(record, task, false);
        }

        RdPutawayTaskStatusEnum current = RdPutawayTaskStatusEnum.valueOf(task.getStatus());
        if (current != RdPutawayTaskStatusEnum.ASSIGNED
                && current != RdPutawayTaskStatusEnum.MOVING) {
            RdPutawayScanRecordDO record = insertScanRecord(reqVO, task, line, packageCode,
                    "STATUS_INVALID", false, "TASK_STATUS_INVALID", "当前任务状态不能继续扫码",
                    statusBefore, statusBefore, warehouseKeeperId, warehouseKeeperName);
            return toScanResponse(record, task, false);
        }

        LocalDateTime now = LocalDateTime.now();
        if (current == RdPutawayTaskStatusEnum.ASSIGNED) {
            task.setStatus(RdPutawayTaskStatusMachine.transition(current,
                    RdPutawayTaskActionEnum.START).getStatus());
            task.setStartTime(now);
        }
        line.setLineStatus(LINE_SCANNED)
                .setScanCount(1)
                .setFirstScanTime(now)
                .setLastScanTime(now)
                .setVersion(nextVersion(line.getVersion()));
        taskLineMapper.updateById(line);

        int scanned = (int) lines.stream().filter(item -> zero(item.getScanCount()) > 0).count();
        task.setScannedPackageCount(scanned);
        if (scanned == task.getPackageCount()) {
            task.setStatus(RdPutawayTaskStatusMachine.transition(
                    RdPutawayTaskStatusEnum.MOVING,
                    RdPutawayTaskActionEnum.COMPLETE_SCANNING).getStatus());
            task.setScanCompleteTime(now);
        }
        task.setVersion(nextVersion(task.getVersion()));
        taskMapper.updateById(task);
        RdPutawayScanRecordDO record = insertScanRecord(reqVO, task, line, packageCode,
                "SUCCESS", true, null, null, statusBefore, task.getStatus(),
                warehouseKeeperId, warehouseKeeperName);
        return toScanResponse(record, task, false);
    }

    @Override
    public RdPutawayBatchScanRespVO previewBatchScan(RdPutawayBatchScanPreviewReqVO reqVO) {
        String code = normalizePackageCode(reqVO.getPackageCode());
        Long taskId = reqVO.getId();
        if (taskId == null) {
            RdPutawayTaskLineDO line = taskLineMapper.selectActiveByPackageCode(code);
            if (line == null) throw exception(PUTAWAY_SCAN_PACKAGE_MISMATCH);
            taskId = line.getTaskId();
        }
        RdPutawayTaskDO task = taskMapper.selectById(taskId);
        if (task == null) throw exception(PUTAWAY_TASK_NOT_EXISTS);
        List<RdPutawayTaskLineDO> lines = taskLineMapper.selectListByTaskId(taskId);
        if (lines.stream().noneMatch(line -> Objects.equals(normalizeCode(line.getPackageCode()), normalizeCode(code)))) {
            throw exception(PUTAWAY_SCAN_PACKAGE_MISMATCH);
        }
        return batchScanResponse(task, lines, code, null, false, 0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = RdLogRecordConstants.PUTAWAY_TYPE, subType = "仓管批次核验上架",
            bizNo = "{{#reqVO.id}}", success = "上架任务【{{#reqVO.id}}】批次代表码【{{#reqVO.packageCode}}】核验")
    public RdPutawayBatchScanRespVO confirmBatchScan(RdPutawayBatchScanConfirmReqVO reqVO,
                                                    Long warehouseKeeperId, String warehouseKeeperName) {
        if (!permissionService.hasAnyPermissions(warehouseKeeperId, "rd:putaway-task:confirm")) {
            throw exception(FORBIDDEN);
        }
        List<Long> selectedIds = reqVO.getTaskLineIds();
        if (reqVO.getId() == null || reqVO.getVersion() == null || !hasText(reqVO.getIdempotencyKey())
                || reqVO.getIdempotencyKey().length() > 128 || selectedIds == null || selectedIds.isEmpty()
                || selectedIds.size() > 500 || selectedIds.stream().anyMatch(id -> id == null || id <= 0)
                || new LinkedHashSet<>(selectedIds).size() != selectedIds.size()) {
            throw exception(PUTAWAY_BATCH_SELECTION_INVALID);
        }
        String code = normalizePackageCode(reqVO.getPackageCode());
        List<Long> orderedIds = selectedIds.stream().sorted().toList();
        Map<String, Object> payload = new java.util.TreeMap<>();
        payload.put("taskId", reqVO.getId());
        payload.put("version", reqVO.getVersion());
        payload.put("packageCode", normalizeCode(code));
        payload.put("lineIds", orderedIds);
        payload.put("operatorId", warehouseKeeperId);
        String digest = DigestUtil.sha256Hex(JsonUtils.toJsonString(payload));
        RdPutawayTaskDO task = taskMapper.selectByIdForUpdate(reqVO.getId());
        if (task == null) throw exception(PUTAWAY_TASK_NOT_EXISTS);
        RdPutawayBatchScanRecordDO replay = batchScanRecordMapper.selectByTaskAndKey(task.getId(), reqVO.getIdempotencyKey());
        if (replay != null) {
            if (!Objects.equals(replay.getRequestDigest(), digest)) throw exception(PUTAWAY_SCAN_KEY_CONFLICT);
            return batchScanResponse(task, taskLineMapper.selectListByTaskId(task.getId()),
                    replay.getRepresentativePackageCode(), replay.getId(), true, 0);
        }
        if (!Objects.equals(reqVO.getVersion(), task.getVersion())) throw exception(PUTAWAY_TASK_VERSION_CONFLICT);
        if (!Set.of("ASSIGNED", "MOVING").contains(task.getStatus())) throw exception(PUTAWAY_TASK_STATUS_INVALID);
        List<RdPutawayTaskLineDO> lines = taskLineMapper.selectListByTaskIdForUpdate(task.getId());
        if (lines.stream().noneMatch(line -> Objects.equals(normalizeCode(line.getPackageCode()), normalizeCode(code)))) {
            throw exception(PUTAWAY_SCAN_PACKAGE_MISMATCH);
        }
        List<RdPutawayTaskLineDO> selected = lines.stream().filter(line -> orderedIds.contains(line.getId()))
                .sorted(Comparator.comparing(RdPutawayTaskLineDO::getPackageId)).toList();
        if (selected.size() != orderedIds.size()) throw exception(PUTAWAY_SCAN_PACKAGE_MISMATCH);
        // Verify the whole selection before the first scan record is written.
        for (RdPutawayTaskLineDO line : selected) {
            if (!LINE_PENDING.equals(line.getLineStatus()) || zero(line.getScanCount()) != 0) {
                throw exception(PUTAWAY_BATCH_SELECTION_INVALID);
            }
            RdPackageUnitDO unit = packageMapper.selectByIdForUpdate(line.getPackageId());
            if (unit == null || !RdPackageStatusEnum.INBOUND_PENDING.getStatus().equals(unit.getStatus())
                    || !Objects.equals(unit.getPackageCode(), line.getPackageCode())
                    || !Objects.equals(unit.getCustomerName(), line.getCustomerName())
                    || !Objects.equals(unit.getProductModel(), line.getProductModel())
                    || !Objects.equals(unit.getFurnaceNo(), line.getFurnaceNo())
                    || !Objects.equals(unit.getFurnaceLayer(), line.getFurnaceLayer())
                    || unit.getPackageWeight() == null || unit.getPackageWeight().compareTo(line.getPackageWeight()) != 0) {
                throw exception(PUTAWAY_CONFIRM_PACKAGE_INVALID);
            }
        }
        for (RdPutawayTaskLineDO line : selected) {
            RdPutawayScanReqVO single = new RdPutawayScanReqVO();
            single.setId(task.getId());
            single.setPackageCode(line.getPackageCode());
            single.setIdempotencyKey("batch:" + DigestUtil.sha256Hex(reqVO.getIdempotencyKey()) + ":" + line.getId());
            single.setDeviceCode(reqVO.getDeviceCode());
            single.setDeviceTime(reqVO.getDeviceTime());
            RdPutawayScanRespVO result = scanPackage(single, warehouseKeeperId, warehouseKeeperName);
            if (!Boolean.TRUE.equals(result.getSuccess()) || Boolean.TRUE.equals(result.getReplayed())) {
                throw exception(PUTAWAY_BATCH_SELECTION_INVALID);
            }
        }
        RdPutawayBatchScanRecordDO record = new RdPutawayBatchScanRecordDO().setTaskId(task.getId())
                .setRepresentativePackageCode(code).setIdempotencyKey(reqVO.getIdempotencyKey())
                .setRequestDigest(digest).setTaskVersion(reqVO.getVersion())
                .setConfirmedLineIds(JsonUtils.toJsonString(orderedIds)).setConfirmedCount(selected.size())
                .setOperatorId(warehouseKeeperId).setOperatorName(warehouseKeeperName)
                .setDeviceCode(reqVO.getDeviceCode()).setScanTime(LocalDateTime.now());
        batchScanRecordMapper.insert(record);
        return batchScanResponse(taskMapper.selectById(task.getId()), taskLineMapper.selectListByTaskId(task.getId()),
                code, record.getId(), false, selected.size());
    }

    private RdPutawayBatchScanRespVO batchScanResponse(RdPutawayTaskDO task, List<RdPutawayTaskLineDO> lines,
                                                     String code, Long recordId, boolean replayed, int confirmedCount) {
        return new RdPutawayBatchScanRespVO().setTask(task).setLines(lines)
                .setExceptions(exceptionMapper.selectListByTaskId(task.getId())).setRepresentativePackageCode(code)
                .setRecordId(recordId).setReplayed(replayed).setConfirmedCount(confirmedCount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = RdLogRecordConstants.PUTAWAY_TYPE,
            subType = RdLogRecordConstants.PUTAWAY_EXCEPTION_SUB_TYPE,
            bizNo = "{{#reqVO.id}}", success = RdLogRecordConstants.PUTAWAY_EXCEPTION_SUCCESS)
    public Long reportException(RdPutawayExceptionCreateReqVO reqVO, Long forkliftDriverId,
                                String forkliftDriverName) {
        if (!EXCEPTION_TYPES.contains(reqVO.getExceptionType())) {
            throw exception(PUTAWAY_EXCEPTION_TYPE_INVALID);
        }
        RdPutawayTaskDO task = taskMapper.selectByIdForUpdate(reqVO.getId());
        if (task == null) {
            throw exception(PUTAWAY_TASK_NOT_EXISTS);
        }
        if (!Objects.equals(task.getForkliftDriverId(), forkliftDriverId)) {
            throw exception(PUTAWAY_TASK_NOT_ASSIGNED);
        }
        RdPutawayTaskStatusEnum current = RdPutawayTaskStatusEnum.valueOf(task.getStatus());
        if (!RdPutawayTaskStatusMachine.canTransition(current,
                RdPutawayTaskActionEnum.REPORT_EXCEPTION)) {
            throw exception(PUTAWAY_TASK_STATUS_INVALID);
        }

        String packageCode = hasText(reqVO.getPackageCode())
                ? normalizePackageCode(reqVO.getPackageCode()) : null;
        List<RdPutawayTaskLineDO> lines = taskLineMapper.selectListByTaskIdForUpdate(task.getId());
        RdPutawayTaskLineDO line = hasText(packageCode) ? lines.stream()
                .filter(item -> Objects.equals(normalizeCode(item.getPackageCode()),
                        normalizeCode(packageCode)))
                .findFirst().orElse(null) : null;
        LocalDateTime now = LocalDateTime.now();
        RdPutawayExceptionDO exceptionDO = new RdPutawayExceptionDO()
                .setExceptionNo(generateExceptionNo())
                .setTaskId(task.getId())
                .setTaskLineId(line == null ? null : line.getId())
                .setPackageId(line == null ? null : line.getPackageId())
                .setPackageCode(packageCode)
                .setExceptionType(reqVO.getExceptionType())
                .setExceptionStatus("OPEN")
                .setDescription(reqVO.getDescription().trim())
                .setAttachmentRefs(reqVO.getAttachmentRefs())
                .setReporterId(forkliftDriverId)
                .setReporterName(forkliftDriverName)
                .setReporterRole("FORKLIFT_DRIVER")
                .setReportedTime(now)
                .setVersion(0);
        exceptionMapper.insert(exceptionDO);
        if (line != null && zero(line.getScanCount()) == 0) {
            line.setLineStatus(LINE_EXCEPTION).setVersion(nextVersion(line.getVersion()));
            taskLineMapper.updateById(line);
        }
        task.setStatus(RdPutawayTaskStatusMachine.transition(current,
                        RdPutawayTaskActionEnum.REPORT_EXCEPTION).getStatus())
                .setExceptionPackageCount(zero(task.getExceptionPackageCount()) + 1)
                .setVersion(nextVersion(task.getVersion()));
        taskMapper.updateById(task);
        return exceptionDO.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = RdLogRecordConstants.PUTAWAY_TYPE,
            subType = RdLogRecordConstants.PUTAWAY_CONFIRM_SUB_TYPE,
            bizNo = "{{#reqVO.id}}", success = RdLogRecordConstants.PUTAWAY_CONFIRM_SUCCESS)
    public Long confirmTask(RdPutawayTaskConfirmReqVO reqVO, Long warehouseKeeperId,
                            String warehouseKeeperName) {
        RdPutawayTaskDO task = taskMapper.selectByIdForUpdate(reqVO.getId());
        if (task == null) {
            throw exception(PUTAWAY_TASK_NOT_EXISTS);
        }
        if (RdPutawayTaskStatusEnum.PUTAWAY_COMPLETED.getStatus().equals(task.getStatus())) {
            return taskLineMapper.selectListByTaskId(task.getId()).stream()
                    .map(RdPutawayTaskLineDO::getInboundOrderId)
                    .filter(Objects::nonNull).findFirst().orElse(null);
        }
        if (!reqVO.getVersion().equals(task.getVersion())) {
            throw exception(PUTAWAY_TASK_VERSION_CONFLICT);
        }
        if (!RdPutawayTaskStatusEnum.PENDING_CONFIRMATION.getStatus().equals(task.getStatus())) {
            throw exception(PUTAWAY_TASK_STATUS_INVALID);
        }
        if (!exceptionMapper.selectOpenListByTaskIdForUpdate(task.getId()).isEmpty()) {
            throw exception(PUTAWAY_CONFIRM_HAS_OPEN_EXCEPTION);
        }
        List<RdPutawayTaskLineDO> lines = taskLineMapper.selectListByTaskIdForUpdate(task.getId());
        if (lines.size() != task.getPackageCount()
                || lines.stream().anyMatch(line -> zero(line.getScanCount()) == 0
                || LINE_EXCEPTION.equals(line.getLineStatus()))) {
            throw exception(PUTAWAY_CONFIRM_PACKAGES_INCOMPLETE);
        }

        Map<Long, RdWarehouseLocationDO> lockedLocations = lockLocations(
                task.getPlannedLocationId(), reqVO.getFinalLocationId());
        lockedLocations.values().forEach(this::reconcileLocationCounters);
        RdWarehouseLocationDO finalLocation = lockedLocations.get(reqVO.getFinalLocationId());
        requireAvailableLocation(finalLocation);
        boolean sameLocation = Objects.equals(task.getPlannedLocationId(), finalLocation.getId());
        BigDecimal ownPackages = sameLocation ? BigDecimal.valueOf(task.getPackageCount())
                : BigDecimal.ZERO;
        BigDecimal ownWeight = sameLocation ? zero(task.getTotalWeight()) : BigDecimal.ZERO;
        requireCapacity(finalLocation, BigDecimal.valueOf(task.getPackageCount()),
                zero(task.getTotalWeight()), ownPackages, ownWeight);

        List<RdPackageUnitDO> packages = new ArrayList<>();
        for (RdPutawayTaskLineDO line : lines) {
            RdPackageUnitDO packageUnit = packageMapper.selectByIdForUpdate(line.getPackageId());
            if (packageUnit == null
                    || !Objects.equals(packageUnit.getPackageCode(), line.getPackageCode())
                    || !RdPackageStatusEnum.INBOUND_PENDING.getStatus().equals(packageUnit.getStatus())) {
                throw exception(PUTAWAY_CONFIRM_PACKAGE_INVALID);
            }
            packages.add(packageUnit);
        }

        LocalDateTime now = LocalDateTime.now();
        RdInboundOrderDO order = createPostedInboundOrder(task, warehouseKeeperId,
                warehouseKeeperName, now);
        for (int index = 0; index < lines.size(); index++) {
            postPutawayLine(task, lines.get(index), packages.get(index), finalLocation, order,
                    warehouseKeeperId, warehouseKeeperName);
        }
        task.setStatus(RdPutawayTaskStatusMachine.transition(
                        RdPutawayTaskStatusEnum.PENDING_CONFIRMATION,
                        RdPutawayTaskActionEnum.CONFIRM_PUTAWAY).getStatus())
                .setFinalLocationId(finalLocation.getId())
                .setFinalLocationCode(finalLocation.getLocationCode())
                .setConfirmTime(now)
                .setExceptionPackageCount(0)
                .setVersion(nextVersion(task.getVersion()));
        taskMapper.updateById(task);

        lockedLocations.values().forEach(this::reconcileLocationCounters);
        updateProductionInboundStatuses(packages);
        return order.getId();
    }

    @Override
    public PageResult<RdPutawayTaskDO> getTaskPage(RdPutawayTaskPageReqVO reqVO) {
        return taskMapper.selectPage(reqVO, new LambdaQueryWrapperX<RdPutawayTaskDO>()
                .likeIfPresent(RdPutawayTaskDO::getTaskNo, reqVO.getTaskNo())
                .eqIfPresent(RdPutawayTaskDO::getStatus, reqVO.getStatus())
                .eqIfPresent(RdPutawayTaskDO::getPlannedLocationId, reqVO.getPlannedLocationId())
                .eqIfPresent(RdPutawayTaskDO::getForkliftDriverId, reqVO.getForkliftDriverId())
                .orderByDesc(RdPutawayTaskDO::getId));
    }

    @Override
    public PageResult<RdPutawayTaskDO> getMyTaskPage(PageParam pageParam, Long forkliftDriverId) {
        return taskMapper.selectPage(pageParam, new LambdaQueryWrapperX<RdPutawayTaskDO>()
                .eq(RdPutawayTaskDO::getForkliftDriverId, forkliftDriverId)
                .in(RdPutawayTaskDO::getStatus, ACTIVE_TASK_STATUSES)
                .orderByAsc(RdPutawayTaskDO::getAssignedTime)
                .orderByAsc(RdPutawayTaskDO::getId));
    }

    @Override
    public RdPutawayTaskDetailRespVO getTaskDetail(Long id) {
        RdPutawayTaskDO task = taskMapper.selectById(id);
        if (task == null) {
            throw exception(PUTAWAY_TASK_NOT_EXISTS);
        }
        return buildTaskDetail(task);
    }

    @Override
    public RdPutawayTaskDetailRespVO getMyTaskDetail(Long id, Long forkliftDriverId) {
        RdPutawayTaskDO task = taskMapper.selectById(id);
        if (task == null) {
            throw exception(PUTAWAY_TASK_NOT_EXISTS);
        }
        if (!Objects.equals(task.getForkliftDriverId(), forkliftDriverId)) {
            throw exception(PUTAWAY_TASK_NOT_ASSIGNED);
        }
        return buildTaskDetail(task);
    }

    private RdPutawayTaskDetailRespVO buildTaskDetail(RdPutawayTaskDO task) {
        RdPutawayTaskDetailRespVO detail = new RdPutawayTaskDetailRespVO();
        detail.setTask(task);
        detail.setLines(taskLineMapper.selectListByTaskId(task.getId()));
        detail.setExceptions(exceptionMapper.selectListByTaskId(task.getId()));
        return detail;
    }

    private RdPutawayScanRecordDO insertScanRecord(RdPutawayScanReqVO reqVO,
                                                    RdPutawayTaskDO task,
                                                    RdPutawayTaskLineDO line,
                                                    String packageCode,
                                                    String scanResult,
                                                    boolean success,
                                                    String errorCode,
                                                    String errorMessage,
                                                    String statusBefore,
                                                    String statusAfter,
                                                    Long operatorId,
                                                    String operatorName) {
        RdPutawayScanRecordDO record = new RdPutawayScanRecordDO()
                .setTaskId(task.getId())
                .setTaskLineId(line == null ? null : line.getId())
                .setPackageId(line == null ? null : line.getPackageId())
                .setPackageCode(limit(packageCode, 32))
                .setIdempotencyKey(reqVO.getIdempotencyKey())
                .setScanResult(scanResult)
                .setSuccess(success)
                .setErrorCode(errorCode)
                .setErrorMessage(errorMessage)
                .setTaskStatusBefore(statusBefore)
                .setTaskStatusAfter(statusAfter)
                .setDeviceCode(reqVO.getDeviceCode())
                .setOperatorId(operatorId)
                .setOperatorName(operatorName)
                .setDeviceTime(reqVO.getDeviceTime())
                .setScanTime(LocalDateTime.now());
        scanRecordMapper.insert(record);
        return record;
    }

    private RdPutawayScanRespVO toScanResponse(RdPutawayScanRecordDO record,
                                                RdPutawayTaskDO task,
                                                boolean replayed) {
        String message = record.getErrorMessage();
        if (!hasText(message)) {
            message = "SUCCESS".equals(record.getScanResult())
                    ? "扫码成功，已写入任务进度" : "扫码请求已处理";
        }
        return new RdPutawayScanRespVO()
                .setRecordId(record.getId())
                .setTaskId(task.getId())
                .setPackageCode(record.getPackageCode())
                .setScanResult(record.getScanResult())
                .setSuccess(record.getSuccess())
                .setReplayed(replayed)
                .setMessage(message)
                .setTaskStatus(task.getStatus())
                .setScannedPackageCount(zero(task.getScannedPackageCount()))
                .setPackageCount(zero(task.getPackageCount()))
                .setScanTime(record.getScanTime());
    }

    private RdInboundOrderDO createPostedInboundOrder(RdPutawayTaskDO task, Long operatorId,
                                                       String operatorName,
                                                       LocalDateTime operationTime) {
        LocalDate bizDate = operationTime.toLocalDate();
        RdInboundOrderDO order = new RdInboundOrderDO()
                .setOrderNo("RK" + bizDate.toString().replace("-", "")
                        + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .setBizDate(bizDate)
                .setSourceType("PUTAWAY_TASK")
                .setTotalPackages(BigDecimal.valueOf(task.getPackageCount()))
                .setTotalWeight(zero(task.getTotalWeight()))
                .setStatus(RdInboundOrderStatusEnum.POSTED.getStatus())
                .setSubmitterId(operatorId)
                .setSubmitterName(operatorName)
                .setSubmitTime(operationTime)
                .setReviewerId(operatorId)
                .setReviewerName(operatorName)
                .setReviewTime(operationTime)
                .setPosterId(operatorId)
                .setPosterName(operatorName)
                .setPostTime(operationTime)
                .setVersion(0)
                .setRemark("上架任务 " + task.getTaskNo() + " 经仓管确认生成");
        inboundOrderMapper.insert(order);
        return order;
    }

    private void postPutawayLine(RdPutawayTaskDO task, RdPutawayTaskLineDO taskLine,
                                 RdPackageUnitDO packageUnit,
                                 RdWarehouseLocationDO finalLocation,
                                 RdInboundOrderDO order,
                                 Long operatorId, String operatorName) {
        String idempotencyKey = "PUTAWAY:" + task.getId() + ":" + packageUnit.getId();
        if (stockTransactionMapper.selectByIdempotencyKey(idempotencyKey) != null) {
            throw exception(PUTAWAY_CONFIRM_PACKAGE_INVALID);
        }
        Long tenantId = TenantContextHolder.getTenantId() == null
                ? 1L : TenantContextHolder.getTenantId();
        RdStockLotDO lot = stockLotMapper.selectInboundLotForUpdate(tenantId,
                finalLocation.getLocationCode(), packageUnit.getCustomerName(),
                packageUnit.getProductModel(), packageUnit.getFurnaceNo(),
                packageUnit.getFurnaceLayer());
        BigDecimal before;
        if (lot == null) {
            before = BigDecimal.ZERO;
            lot = new RdStockLotDO()
                    .setLocationId(finalLocation.getId())
                    .setLocationCode(finalLocation.getLocationCode())
                    .setCustomerName(packageUnit.getCustomerName())
                    .setProductModel(packageUnit.getProductModel())
                    .setFurnaceNo(packageUnit.getFurnaceNo())
                    .setFurnaceLayer(packageUnit.getFurnaceLayer())
                    .setOpeningQty(BigDecimal.ZERO)
                    .setTransferInQty(BigDecimal.ZERO)
                    .setTransferOutQty(BigDecimal.ZERO)
                    .setInboundQty(BigDecimal.ONE)
                    .setOutboundQty(BigDecimal.ZERO)
                    .setReturnQty(BigDecimal.ZERO)
                    .setWarehouseQty(BigDecimal.ONE)
                    .setLockedQty(BigDecimal.ZERO)
                    .setPackageWeight(packageUnit.getPackageWeight())
                    .setStockWeight(packageUnit.getPackageWeight())
                    .setLastBizDate(order.getBizDate())
                    .setStatus(RdStockLotStatusEnum.AVAILABLE.getStatus())
                    .setRemark("仓管确认上架生成");
            stockLotMapper.insert(lot);
        } else {
            before = zero(lot.getWarehouseQty());
            lot.setInboundQty(zero(lot.getInboundQty()).add(BigDecimal.ONE))
                    .setWarehouseQty(before.add(BigDecimal.ONE))
                    .setStockWeight(zero(lot.getStockWeight()).add(packageUnit.getPackageWeight()))
                    .setLastBizDate(order.getBizDate())
                    .setStatus(RdStockLotStatusEnum.AVAILABLE.getStatus());
            stockLotMapper.updateById(lot);
        }

        RdStockTransactionDO transaction = new RdStockTransactionDO()
                .setBizDate(order.getBizDate())
                .setTransactionType(RdStockTransactionTypeEnum.PRODUCTION_IN.getType())
                .setStockLotId(lot.getId())
                .setPackageId(packageUnit.getId())
                .setLocationId(finalLocation.getId())
                .setLocationCode(finalLocation.getLocationCode())
                .setCustomerName(packageUnit.getCustomerName())
                .setProductModel(packageUnit.getProductModel())
                .setFurnaceNo(packageUnit.getFurnaceNo())
                .setFurnaceLayer(packageUnit.getFurnaceLayer())
                .setQuantity(BigDecimal.ONE)
                .setPackageWeight(packageUnit.getPackageWeight())
                .setWeight(packageUnit.getPackageWeight())
                .setBeforeQty(before)
                .setAfterQty(before.add(BigDecimal.ONE))
                .setSourceType("PUTAWAY_TASK")
                .setSourceNo(task.getTaskNo())
                .setSourceLineNo(packageUnit.getPackageCode())
                .setRelatedOrderId(order.getId())
                .setOperatorId(operatorId)
                .setOperatorName(operatorName)
                .setReviewerId(operatorId)
                .setReviewerName(operatorName)
                .setIdempotencyKey(idempotencyKey)
                .setRemark("仓管确认包码上架");
        stockTransactionMapper.insert(transaction);

        packageUnit.setStatus(RdPackageStatusEnum.IN_STOCK.getStatus())
                .setLocationId(finalLocation.getId())
                .setLocationCode(finalLocation.getLocationCode())
                .setStockLotId(lot.getId())
                .setVersion(nextVersion(packageUnit.getVersion()));
        packageMapper.updateById(packageUnit);

        RdInboundOrderLineDO inboundLine = new RdInboundOrderLineDO()
                .setOrderId(order.getId())
                .setPackageId(packageUnit.getId())
                .setLocationId(finalLocation.getId())
                .setLocationCode(finalLocation.getLocationCode())
                .setQuantity(BigDecimal.ONE)
                .setWeight(packageUnit.getPackageWeight())
                .setStockLotId(lot.getId())
                .setStockTransactionId(transaction.getId())
                .setRemark("来源上架任务 " + task.getTaskNo());
        inboundOrderLineMapper.insert(inboundLine);
        taskLine.setLineStatus(LINE_CONFIRMED)
                .setInboundOrderId(order.getId())
                .setInboundOrderLineId(inboundLine.getId())
                .setStockLotId(lot.getId())
                .setStockTransactionId(transaction.getId())
                .setVersion(nextVersion(taskLine.getVersion()));
        taskLineMapper.updateById(taskLine);
    }

    private void updateProductionInboundStatuses(List<RdPackageUnitDO> packages) {
        packages.stream().map(RdPackageUnitDO::getProductionOutputId)
                .filter(Objects::nonNull).distinct().forEach(productionOutputId -> {
                    RdProductionOutputDO production = productionOutputMapper
                            .selectByIdForUpdate(productionOutputId);
                    if (production == null) {
                        return;
                    }
                    List<RdPackageUnitDO> units = packageMapper
                            .selectListByProductionOutputId(productionOutputId);
                    long inbound = units.stream().filter(unit ->
                            RdPackageStatusEnum.IN_STOCK.getStatus().equals(unit.getStatus())
                                    || RdPackageStatusEnum.OUTBOUND_PENDING.getStatus()
                                    .equals(unit.getStatus())
                                    || RdPackageStatusEnum.OUTBOUNDED.getStatus()
                                    .equals(unit.getStatus())).count();
                    production.setInboundQty(BigDecimal.valueOf(inbound));
                    production.setStatus(!units.isEmpty() && inbound == units.size()
                            ? RdProductionOutputStatusEnum.INBOUNDED.getStatus()
                            : RdProductionOutputStatusEnum.CONFIRMED.getStatus());
                    productionOutputMapper.updateById(production);
                });
    }

    private RdPutawayLocationMapRespVO toLocationMap(RdWarehouseLocationDO location,
                                                      RdLocationCapacitySummaryDO occupancy,
                                                      RdLocationCapacitySummaryDO reservation) {
        RdPutawayLocationMapRespVO result = BeanUtils.toBean(location, RdPutawayLocationMapRespVO.class);
        BigDecimal occupiedPackages = occupancy == null ? BigDecimal.ZERO : zero(occupancy.getPackageCount());
        BigDecimal occupiedWeight = occupancy == null ? BigDecimal.ZERO : zero(occupancy.getTotalWeight());
        BigDecimal reservedPackages = reservation == null ? BigDecimal.ZERO : zero(reservation.getPackageCount());
        BigDecimal reservedWeight = reservation == null ? BigDecimal.ZERO : zero(reservation.getTotalWeight());
        result.setOccupiedPackages(occupiedPackages);
        result.setOccupiedWeight(occupiedWeight);
        result.setReservedPackages(reservedPackages);
        result.setReservedWeight(reservedWeight);
        result.setAvailablePackages(available(location.getCapacityPackages(), occupiedPackages, reservedPackages));
        result.setAvailableWeight(available(location.getCapacityWeight(), occupiedWeight, reservedWeight));
        result.setUsagePercent(usagePercent(location, occupiedPackages, occupiedWeight,
                reservedPackages, reservedWeight));
        result.setDisplayStatus(displayStatus(location, occupiedPackages.add(reservedPackages),
                occupiedWeight.add(reservedWeight)));
        return result;
    }

    private AdminUserDO requireForkliftDriver(Long userId) {
        AdminUserDO user = adminUserService.getUser(userId);
        if (user == null || !CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus())
                || !permissionService.hasAnyRoles(userId, FORKLIFT_DRIVER_ROLE)) {
            throw exception(PUTAWAY_DRIVER_INVALID);
        }
        return user;
    }

    private RdWarehouseLocationDO requireAvailableLocationForUpdate(Long locationId) {
        RdWarehouseLocationDO location = locationMapper.selectByIdForUpdate(locationId);
        requireAvailableLocation(location);
        return location;
    }

    private void requireAvailableLocation(RdWarehouseLocationDO location) {
        if (location == null || !Boolean.TRUE.equals(location.getEnabled())
                || Boolean.TRUE.equals(location.getFrozen())
                || (hasText(location.getLocationStatus())
                && !"AVAILABLE".equals(location.getLocationStatus()))) {
            throw exception(PUTAWAY_LOCATION_INVALID);
        }
    }

    private void validatePendingPackage(RdPackageUnitDO packageUnit) {
        if (packageUnit == null
                || !RdPackageStatusEnum.QC_PASSED.getStatus().equals(packageUnit.getStatus())) {
            throw exception(PUTAWAY_PACKAGE_STATUS_INVALID);
        }
        if (!hasText(packageUnit.getPackageCode()) || !hasText(packageUnit.getCustomerName())
                || !hasText(packageUnit.getProductModel()) || !hasText(packageUnit.getFurnaceNo())
                || !hasText(packageUnit.getFurnaceLayer()) || packageUnit.getPackageWeight() == null
                || packageUnit.getPackageWeight().signum() <= 0) {
            throw exception(PUTAWAY_PACKAGE_DATA_INCOMPLETE);
        }
    }

    private void requireCapacity(RdWarehouseLocationDO location, BigDecimal packageCount,
                                 BigDecimal totalWeight, BigDecimal ownPackages,
                                 BigDecimal ownWeight) {
        BigDecimal usedPackages = zero(location.getOccupiedPackages())
                .add(zero(location.getReservedPackages())).subtract(ownPackages);
        BigDecimal usedWeight = zero(location.getOccupiedWeight())
                .add(zero(location.getReservedWeight())).subtract(ownWeight);
        if (hasLimit(location.getCapacityPackages())
                && usedPackages.add(packageCount).compareTo(location.getCapacityPackages()) > 0) {
            throw exception(PUTAWAY_LOCATION_CAPACITY_INSUFFICIENT);
        }
        if (hasLimit(location.getCapacityWeight())
                && usedWeight.add(totalWeight).compareTo(location.getCapacityWeight()) > 0) {
            throw exception(PUTAWAY_LOCATION_CAPACITY_INSUFFICIENT);
        }
    }

    private void reconcileLocationCounters(RdWarehouseLocationDO location) {
        RdLocationCapacitySummaryDO occupancy = packageMapper
                .selectLocationOccupancySummaryByLocationId(location.getId(), PHYSICAL_STOCK_STATUSES);
        RdLocationCapacitySummaryDO reservation = taskMapper
                .selectLocationReservationSummaryByLocationId(location.getId(), ACTIVE_TASK_STATUSES);
        if (occupancy == null || reservation == null) {
            return;
        }
        BigDecimal occupiedPackages = zero(occupancy.getPackageCount());
        BigDecimal occupiedWeight = zero(occupancy.getTotalWeight());
        BigDecimal reservedPackages = zero(reservation.getPackageCount());
        BigDecimal reservedWeight = zero(reservation.getTotalWeight());
        boolean changed = different(location.getOccupiedPackages(), occupiedPackages)
                || different(location.getOccupiedWeight(), occupiedWeight)
                || different(location.getReservedPackages(), reservedPackages)
                || different(location.getReservedWeight(), reservedWeight);
        location.setOccupiedPackages(occupiedPackages)
                .setOccupiedWeight(occupiedWeight)
                .setReservedPackages(reservedPackages)
                .setReservedWeight(reservedWeight);
        if (changed) {
            location.setVersion(nextVersion(location.getVersion()));
            locationMapper.updateById(location);
        }
    }

    private Map<Long, RdWarehouseLocationDO> lockLocations(Long firstId, Long secondId) {
        return Stream.of(firstId, secondId).distinct().sorted()
                .map(locationMapper::selectByIdForUpdate)
                .filter(item -> item != null)
                .collect(Collectors.toMap(RdWarehouseLocationDO::getId, Function.identity()));
    }

    private void reserveLocation(RdWarehouseLocationDO location, BigDecimal packageCount,
                                 BigDecimal totalWeight) {
        location.setReservedPackages(zero(location.getReservedPackages()).add(packageCount));
        location.setReservedWeight(zero(location.getReservedWeight()).add(totalWeight));
        location.setVersion(nextVersion(location.getVersion()));
        locationMapper.updateById(location);
    }

    private void releaseLocation(RdWarehouseLocationDO location, BigDecimal packageCount,
                                 BigDecimal totalWeight) {
        if (location == null) {
            throw exception(PUTAWAY_LOCATION_INVALID);
        }
        location.setReservedPackages(nonNegative(zero(location.getReservedPackages()).subtract(packageCount)));
        location.setReservedWeight(nonNegative(zero(location.getReservedWeight()).subtract(totalWeight)));
        location.setVersion(nextVersion(location.getVersion()));
        locationMapper.updateById(location);
    }

    private static String generateTaskNo() {
        return "SJ" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private static String generateExceptionNo() {
        return "YC" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private static String normalizePackageCode(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        try {
            if (value.startsWith("http://") || value.startsWith("https://")) {
                String path = URI.create(value).getPath();
                int separator = path.lastIndexOf('/');
                value = separator >= 0 ? path.substring(separator + 1) : path;
                value = URLDecoder.decode(value, StandardCharsets.UTF_8.name());
            } else {
                int marker = value.toLowerCase(Locale.ROOT).lastIndexOf("/pkg/");
                if (marker >= 0) {
                    value = value.substring(marker + 5);
                }
                int query = value.indexOf('?');
                if (query >= 0) {
                    value = value.substring(0, query);
                }
                int fragment = value.indexOf('#');
                if (fragment >= 0) {
                    value = value.substring(0, fragment);
                }
            }
        } catch (Exception ignored) {
            // Keep the trimmed scanner input so a malformed code can still be audited as a mismatch.
        }
        return value.trim();
    }

    private static String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String limit(String value, int maxLength) {
        String result = value == null ? "" : value;
        return result.length() <= maxLength ? result : result.substring(0, maxLength);
    }

    private static String displayName(AdminUserDO user) {
        return hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }

    private static BigDecimal available(BigDecimal capacity, BigDecimal occupied, BigDecimal reserved) {
        if (!hasLimit(capacity)) {
            return null;
        }
        return nonNegative(capacity.subtract(occupied).subtract(reserved));
    }

    private static BigDecimal usagePercent(RdWarehouseLocationDO location,
                                           BigDecimal occupiedPackages, BigDecimal occupiedWeight,
                                           BigDecimal reservedPackages, BigDecimal reservedWeight) {
        if (hasLimit(location.getCapacityPackages())) {
            return occupiedPackages.add(reservedPackages)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(location.getCapacityPackages(), 1, RoundingMode.HALF_UP);
        }
        if (hasLimit(location.getCapacityWeight())) {
            return occupiedWeight.add(reservedWeight)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(location.getCapacityWeight(), 1, RoundingMode.HALF_UP);
        }
        return null;
    }

    private static String displayStatus(RdWarehouseLocationDO location, BigDecimal usedPackages,
                                        BigDecimal usedWeight) {
        if (!Boolean.TRUE.equals(location.getEnabled())) {
            return "DISABLED";
        }
        if (Boolean.TRUE.equals(location.getFrozen())) {
            return "FROZEN";
        }
        if (hasText(location.getLocationStatus()) && !"AVAILABLE".equals(location.getLocationStatus())) {
            return location.getLocationStatus();
        }
        if ((hasLimit(location.getCapacityPackages())
                && usedPackages.compareTo(location.getCapacityPackages()) >= 0)
                || (hasLimit(location.getCapacityWeight())
                && usedWeight.compareTo(location.getCapacityWeight()) >= 0)) {
            return "FULL";
        }
        return usedPackages.signum() == 0 && usedWeight.signum() == 0 ? "EMPTY" : "PARTIAL";
    }

    private static String appendRemark(String oldRemark, String newRemark) {
        String value = hasText(oldRemark) ? oldRemark + "；" + newRemark : newRemark;
        return value.length() > 512 ? value.substring(value.length() - 512) : value;
    }

    private static boolean hasLimit(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static int nextVersion(Integer version) {
        return version == null ? 1 : version + 1;
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static int zero(Integer value) {
        return value == null ? 0 : value;
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        return value.signum() < 0 ? BigDecimal.ZERO : value;
    }

    private static boolean different(BigDecimal first, BigDecimal second) {
        return zero(first).compareTo(zero(second)) != 0;
    }
}
