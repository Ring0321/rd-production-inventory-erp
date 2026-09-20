package com.ruiding.erp.service.putaway;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import com.ruiding.erp.controller.admin.putaway.vo.RdPendingPutawayPackagePageReqVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayDriverOptionRespVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayLocationMapRespVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayTaskCreateReqVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayTaskDetailRespVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayTaskPageReqVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayTaskReassignReqVO;
import com.ruiding.erp.dal.dataobject.masterdata.RdWarehouseLocationDO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.putaway.RdLocationCapacitySummaryDO;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayExceptionDO;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayTaskDO;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayTaskLineDO;
import com.ruiding.erp.dal.mysql.masterdata.RdWarehouseLocationMapper;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.dal.mysql.putaway.RdPutawayExceptionMapper;
import com.ruiding.erp.dal.mysql.putaway.RdPutawayTaskLineMapper;
import com.ruiding.erp.dal.mysql.putaway.RdPutawayTaskMapper;
import com.ruiding.erp.enums.RdPackageStatusEnum;
import com.ruiding.erp.enums.RdPutawayTaskActionEnum;
import com.ruiding.erp.enums.RdPutawayTaskStatusEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.ruiding.erp.enums.ErrorCodeConstants.PUTAWAY_DRIVER_INVALID;
import static com.ruiding.erp.enums.ErrorCodeConstants.PUTAWAY_LOCATION_CAPACITY_INSUFFICIENT;
import static com.ruiding.erp.enums.ErrorCodeConstants.PUTAWAY_LOCATION_INVALID;
import static com.ruiding.erp.enums.ErrorCodeConstants.PUTAWAY_PACKAGE_DATA_INCOMPLETE;
import static com.ruiding.erp.enums.ErrorCodeConstants.PUTAWAY_PACKAGE_DUPLICATE;
import static com.ruiding.erp.enums.ErrorCodeConstants.PUTAWAY_PACKAGE_STATUS_INVALID;
import static com.ruiding.erp.enums.ErrorCodeConstants.PUTAWAY_REASSIGN_REASON_REQUIRED;
import static com.ruiding.erp.enums.ErrorCodeConstants.PUTAWAY_TASK_NOT_EXISTS;
import static com.ruiding.erp.enums.ErrorCodeConstants.PUTAWAY_TASK_STATUS_INVALID;
import static com.ruiding.erp.enums.ErrorCodeConstants.PUTAWAY_TASK_VERSION_CONFLICT;

@Service
public class RdPutawayTaskServiceImpl implements RdPutawayTaskService {
    private static final String FORKLIFT_DRIVER_ROLE = "rd_forklift_driver";
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
        return task.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
        RdPutawayTaskDetailRespVO detail = new RdPutawayTaskDetailRespVO();
        detail.setTask(task);
        detail.setLines(taskLineMapper.selectListByTaskId(id));
        detail.setExceptions(exceptionMapper.selectListByTaskId(id));
        return detail;
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

    private static BigDecimal nonNegative(BigDecimal value) {
        return value.signum() < 0 ? BigDecimal.ZERO : value;
    }

    private static boolean different(BigDecimal first, BigDecimal second) {
        return zero(first).compareTo(zero(second)) != 0;
    }
}
