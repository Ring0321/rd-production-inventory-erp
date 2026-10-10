package com.ruiding.erp.service.putaway;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayExceptionCreateReqVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayLocationMapRespVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayScanReqVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayTaskConfirmReqVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayTaskCreateReqVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayTaskReassignReqVO;
import com.ruiding.erp.dal.dataobject.inbound.RdInboundOrderDO;
import com.ruiding.erp.dal.dataobject.inbound.RdInboundOrderLineDO;
import com.ruiding.erp.dal.dataobject.masterdata.RdWarehouseLocationDO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.putaway.RdLocationCapacitySummaryDO;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayExceptionDO;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayScanRecordDO;
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
import com.ruiding.erp.dal.mysql.putaway.RdPutawayTaskLineMapper;
import com.ruiding.erp.dal.mysql.putaway.RdPutawayTaskMapper;
import com.ruiding.erp.dal.mysql.production.RdProductionOutputMapper;
import com.ruiding.erp.dal.mysql.stock.RdStockLotMapper;
import com.ruiding.erp.dal.mysql.stock.RdStockTransactionMapper;
import com.ruiding.erp.enums.RdPackageStatusEnum;
import com.ruiding.erp.enums.RdPutawayTaskStatusEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyLong;

@ExtendWith(MockitoExtension.class)
class RdPutawayTaskServiceImplTest {
    @Mock private RdPackageUnitMapper packageMapper;
    @Mock private RdWarehouseLocationMapper locationMapper;
    @Mock private RdPutawayTaskMapper taskMapper;
    @Mock private RdPutawayTaskLineMapper taskLineMapper;
    @Mock private RdPutawayExceptionMapper exceptionMapper;
    @Mock private RdPutawayScanRecordMapper scanRecordMapper;
    @Mock private RdInboundOrderMapper inboundOrderMapper;
    @Mock private RdInboundOrderLineMapper inboundOrderLineMapper;
    @Mock private RdStockLotMapper stockLotMapper;
    @Mock private RdStockTransactionMapper stockTransactionMapper;
    @Mock private RdProductionOutputMapper productionOutputMapper;
    @Mock private AdminUserService adminUserService;
    @Mock private PermissionService permissionService;
    @InjectMocks private RdPutawayTaskServiceImpl service;

    @Test
    void shouldCreateAssignedTaskAndReserveLocation() {
        mockDriver(30L);
        RdWarehouseLocationDO location = availableLocation(10L, "A-01", "10", "10000");
        when(locationMapper.selectByIdForUpdate(10L)).thenReturn(location);
        when(packageMapper.selectByIdForUpdate(1L)).thenReturn(packageUnit(1L, "PK-001", "600"));
        when(packageMapper.selectByIdForUpdate(2L)).thenReturn(packageUnit(2L, "PK-002", "1000"));
        doAnswer(invocation -> {
            ((RdPutawayTaskDO) invocation.getArgument(0)).setId(50L);
            return 1;
        }).when(taskMapper).insert(any(RdPutawayTaskDO.class));
        RdPutawayTaskCreateReqVO reqVO = new RdPutawayTaskCreateReqVO();
        reqVO.setPlannedLocationId(10L);
        reqVO.setForkliftDriverId(30L);
        reqVO.setPackageIds(List.of(2L, 1L));
        reqVO.setRemark("优先上架");

        Long id = service.createTask(reqVO, 20L, "仓管员");

        assertEquals(50L, id);
        ArgumentCaptor<RdPutawayTaskDO> taskCaptor = ArgumentCaptor.forClass(RdPutawayTaskDO.class);
        verify(taskMapper).insert(taskCaptor.capture());
        assertEquals(RdPutawayTaskStatusEnum.ASSIGNED.getStatus(), taskCaptor.getValue().getStatus());
        assertEquals(new BigDecimal("1600"), taskCaptor.getValue().getTotalWeight());
        verify(taskLineMapper, times(2)).insert(any(RdPutawayTaskLineDO.class));
        verify(packageMapper, times(2)).updateById(any(RdPackageUnitDO.class));
        assertEquals(new BigDecimal("2"), location.getReservedPackages());
        assertEquals(new BigDecimal("1600"), location.getReservedWeight());
        assertEquals(1, location.getVersion());
    }

    @Test
    void shouldRejectTaskWhenLocationCapacityIsInsufficient() {
        mockDriver(30L);
        RdWarehouseLocationDO location = availableLocation(10L, "A-01", "1", "1000")
                .setOccupiedPackages(BigDecimal.ONE)
                .setOccupiedWeight(new BigDecimal("1000"));
        when(locationMapper.selectByIdForUpdate(10L)).thenReturn(location);
        when(packageMapper.selectByIdForUpdate(1L)).thenReturn(packageUnit(1L, "PK-001", "600"));
        RdPutawayTaskCreateReqVO reqVO = new RdPutawayTaskCreateReqVO();
        reqVO.setPlannedLocationId(10L);
        reqVO.setForkliftDriverId(30L);
        reqVO.setPackageIds(List.of(1L));

        assertThrows(RuntimeException.class, () -> service.createTask(reqVO, 20L, "仓管员"));
        verify(taskMapper, never()).insert(any(RdPutawayTaskDO.class));
    }

    @Test
    void shouldReconcileLocationCountersBeforeCapacityCheck() {
        mockDriver(30L);
        RdWarehouseLocationDO location = availableLocation(10L, "A-01", "1", "1000");
        when(locationMapper.selectByIdForUpdate(10L)).thenReturn(location);
        when(packageMapper.selectByIdForUpdate(1L)).thenReturn(packageUnit(1L, "PK-001", "600"));
        when(packageMapper.selectLocationOccupancySummaryByLocationId(eq(10L), any()))
                .thenReturn(summary(10L, "1", "1000"));
        when(taskMapper.selectLocationReservationSummaryByLocationId(eq(10L), any()))
                .thenReturn(summary(10L, "0", "0"));
        RdPutawayTaskCreateReqVO reqVO = new RdPutawayTaskCreateReqVO();
        reqVO.setPlannedLocationId(10L);
        reqVO.setForkliftDriverId(30L);
        reqVO.setPackageIds(List.of(1L));

        assertThrows(RuntimeException.class, () -> service.createTask(reqVO, 20L, "仓管员"));

        assertEquals(new BigDecimal("1"), location.getOccupiedPackages());
        assertEquals(new BigDecimal("1000"), location.getOccupiedWeight());
        verify(locationMapper).updateById(location);
        verify(taskMapper, never()).insert(any(RdPutawayTaskDO.class));
    }

    @Test
    void shouldRejectPackageThatIsNotQualityPassed() {
        mockDriver(30L);
        when(locationMapper.selectByIdForUpdate(10L))
                .thenReturn(availableLocation(10L, "A-01", "10", "10000"));
        when(packageMapper.selectByIdForUpdate(1L)).thenReturn(packageUnit(1L, "PK-001", "600")
                .setStatus(RdPackageStatusEnum.INBOUND_PENDING.getStatus()));
        RdPutawayTaskCreateReqVO reqVO = new RdPutawayTaskCreateReqVO();
        reqVO.setPlannedLocationId(10L);
        reqVO.setForkliftDriverId(30L);
        reqVO.setPackageIds(List.of(1L));

        assertThrows(RuntimeException.class, () -> service.createTask(reqVO, 20L, "仓管员"));
        verify(taskMapper, never()).insert(any(RdPutawayTaskDO.class));
    }

    @Test
    void shouldReassignExceptionTaskAndMoveReservation() {
        mockDriver(31L);
        RdPutawayTaskDO task = new RdPutawayTaskDO().setId(50L).setTaskNo("SJ-001")
                .setStatus(RdPutawayTaskStatusEnum.EXCEPTION_PENDING.getStatus())
                .setPlannedLocationId(10L).setPlannedLocationCode("A-01")
                .setPackageCount(2).setTotalWeight(new BigDecimal("1600")).setVersion(3);
        RdWarehouseLocationDO oldLocation = availableLocation(10L, "A-01", "10", "10000")
                .setReservedPackages(new BigDecimal("2")).setReservedWeight(new BigDecimal("1600"));
        RdWarehouseLocationDO newLocation = availableLocation(11L, "A-02", "10", "10000");
        RdPutawayExceptionDO openException = new RdPutawayExceptionDO().setId(70L).setTaskId(50L)
                .setExceptionStatus("OPEN").setVersion(0);
        when(taskMapper.selectByIdForUpdate(50L)).thenReturn(task);
        when(locationMapper.selectByIdForUpdate(10L)).thenReturn(oldLocation);
        when(locationMapper.selectByIdForUpdate(11L)).thenReturn(newLocation);
        when(exceptionMapper.selectOpenListByTaskIdForUpdate(50L)).thenReturn(List.of(openException));
        RdPutawayTaskReassignReqVO reqVO = new RdPutawayTaskReassignReqVO();
        reqVO.setId(50L);
        reqVO.setVersion(3);
        reqVO.setPlannedLocationId(11L);
        reqVO.setForkliftDriverId(31L);
        reqVO.setHandlingResult("原库位已满，调整至 A-02");

        service.reassignTask(reqVO, 20L, "仓管员");

        assertEquals(RdPutawayTaskStatusEnum.ASSIGNED.getStatus(), task.getStatus());
        assertEquals(11L, task.getPlannedLocationId());
        assertEquals(BigDecimal.ZERO, oldLocation.getReservedPackages());
        assertEquals(new BigDecimal("2"), newLocation.getReservedPackages());
        assertEquals("RESOLVED", openException.getExceptionStatus());
        assertEquals("REASSIGN", openException.getResolutionAction());
        verify(exceptionMapper).updateById(openException);
    }

    @Test
    void shouldBuildLocationMapFromPhysicalStockAndActiveReservations() {
        RdWarehouseLocationDO location = availableLocation(10L, "A-01", "10", "10000");
        when(locationMapper.selectList(any())).thenReturn(List.of(location));
        when(packageMapper.selectLocationOccupancySummary(any())).thenReturn(List.of(
                summary(10L, "4", "3600")));
        when(taskMapper.selectLocationReservationSummary(any())).thenReturn(List.of(
                summary(10L, "2", "1600")));

        List<RdPutawayLocationMapRespVO> result = service.getLocationMap(null, null);

        assertEquals(1, result.size());
        assertEquals(new BigDecimal("4"), result.get(0).getOccupiedPackages());
        assertEquals(new BigDecimal("2"), result.get(0).getReservedPackages());
        assertEquals(new BigDecimal("4"), result.get(0).getAvailablePackages());
        assertEquals("PARTIAL", result.get(0).getDisplayStatus());
        assertEquals(new BigDecimal("60.0"), result.get(0).getUsagePercent());
        assertNull(result.get(0).getLocationName());
    }

    @Test
    void shouldReturnPutawayTaskDetailForAssignedDriver() {
        RdPutawayTaskDO task = new RdPutawayTaskDO().setId(50L).setForkliftDriverId(30L);
        RdPutawayTaskLineDO line = new RdPutawayTaskLineDO().setId(60L).setTaskId(50L);
        RdPutawayExceptionDO putawayException = new RdPutawayExceptionDO().setId(70L).setTaskId(50L);
        when(taskMapper.selectById(50L)).thenReturn(task);
        when(taskLineMapper.selectListByTaskId(50L)).thenReturn(List.of(line));
        when(exceptionMapper.selectListByTaskId(50L)).thenReturn(List.of(putawayException));

        var result = service.getMyTaskDetail(50L, 30L);

        assertEquals(task, result.getTask());
        assertEquals(List.of(line), result.getLines());
        assertEquals(List.of(putawayException), result.getExceptions());
    }

    @Test
    void shouldRejectPutawayTaskDetailForOtherDriver() {
        when(taskMapper.selectById(50L))
                .thenReturn(new RdPutawayTaskDO().setId(50L).setForkliftDriverId(31L));

        assertThrows(RuntimeException.class, () -> service.getMyTaskDetail(50L, 30L));

        verify(taskLineMapper, never()).selectListByTaskId(50L);
        verify(exceptionMapper, never()).selectListByTaskId(50L);
    }

    @Test
    void shouldPersistScanAndMoveLastPackageToPendingConfirmation() {
        allowKeeperScan();
        RdPutawayTaskDO task = putawayTask(RdPutawayTaskStatusEnum.ASSIGNED, 1, 0, 0);
        RdPutawayTaskLineDO line = putawayLine("PK-001", "PENDING", 0);
        when(taskMapper.selectByIdForUpdate(50L)).thenReturn(task);
        when(scanRecordMapper.selectByTaskAndIdempotencyKey(50L, "scan-001")).thenReturn(null);
        when(taskLineMapper.selectListByTaskIdForUpdate(50L)).thenReturn(List.of(line));
        doAnswer(invocation -> {
            ((RdPutawayScanRecordDO) invocation.getArgument(0)).setId(70L);
            return 1;
        }).when(scanRecordMapper).insert(any(RdPutawayScanRecordDO.class));

        var result = service.scanPackage(scanReq("PK-001", "scan-001"), 20L, "仓管20");

        assertEquals(Boolean.TRUE, result.getSuccess());
        assertEquals(Boolean.FALSE, result.getReplayed());
        assertEquals("SUCCESS", result.getScanResult());
        assertEquals(1, result.getScannedPackageCount());
        assertEquals(RdPutawayTaskStatusEnum.PENDING_CONFIRMATION.getStatus(), result.getTaskStatus());
        assertEquals("SCANNED", line.getLineStatus());
        assertEquals(1, line.getScanCount());
        assertEquals(RdPutawayTaskStatusEnum.PENDING_CONFIRMATION.getStatus(), task.getStatus());
        verify(taskLineMapper).updateById(line);
        verify(taskMapper).updateById(task);
    }

    @Test
    void shouldReturnOriginalScanResultForIdempotentRetry() {
        allowKeeperScan();
        RdPutawayTaskDO task = putawayTask(RdPutawayTaskStatusEnum.PENDING_CONFIRMATION, 1, 1, 1);
        RdPutawayScanRecordDO record = new RdPutawayScanRecordDO().setId(70L).setTaskId(50L)
                .setPackageCode("PK-001").setIdempotencyKey("scan-001")
                .setScanResult("SUCCESS").setSuccess(true).setOperatorId(20L);
        when(taskMapper.selectByIdForUpdate(50L)).thenReturn(task);
        when(scanRecordMapper.selectByTaskAndIdempotencyKey(50L, "scan-001"))
                .thenReturn(record);

        var result = service.scanPackage(scanReq("PK-001", "scan-001"), 20L, "仓管20");

        assertEquals(Boolean.TRUE, result.getSuccess());
        assertEquals(Boolean.TRUE, result.getReplayed());
        assertEquals(1, result.getScannedPackageCount());
        verify(taskLineMapper, never()).selectListByTaskIdForUpdate(50L);
        verify(scanRecordMapper, never()).insert(any(RdPutawayScanRecordDO.class));
    }

    @Test
    void shouldRecordDuplicatePackageWithoutIncreasingProgress() {
        allowKeeperScan();
        RdPutawayTaskDO task = putawayTask(RdPutawayTaskStatusEnum.MOVING, 2, 1, 1);
        RdPutawayTaskLineDO line = putawayLine("PK-001", "SCANNED", 1);
        when(taskMapper.selectByIdForUpdate(50L)).thenReturn(task);
        when(scanRecordMapper.selectByTaskAndIdempotencyKey(50L, "scan-002")).thenReturn(null);
        when(taskLineMapper.selectListByTaskIdForUpdate(50L)).thenReturn(List.of(line));

        var result = service.scanPackage(scanReq("PK-001", "scan-002"), 20L, "仓管20");

        assertEquals(Boolean.FALSE, result.getSuccess());
        assertEquals("DUPLICATE", result.getScanResult());
        assertEquals(1, result.getScannedPackageCount());
        verify(taskLineMapper, never()).updateById(any(RdPutawayTaskLineDO.class));
        verify(taskMapper, never()).updateById(any(RdPutawayTaskDO.class));
        verify(scanRecordMapper).insert(any(RdPutawayScanRecordDO.class));
    }

    @Test
    void shouldRecordWrongPackageWithoutChangingTask() {
        allowKeeperScan();
        RdPutawayTaskDO task = putawayTask(RdPutawayTaskStatusEnum.ASSIGNED, 1, 0, 0);
        when(taskMapper.selectByIdForUpdate(50L)).thenReturn(task);
        when(scanRecordMapper.selectByTaskAndIdempotencyKey(50L, "scan-wrong")).thenReturn(null);
        when(taskLineMapper.selectListByTaskIdForUpdate(50L))
                .thenReturn(List.of(putawayLine("PK-001", "PENDING", 0)));

        var result = service.scanPackage(scanReq("PK-WRONG", "scan-wrong"), 20L, "仓管20");

        assertEquals(Boolean.FALSE, result.getSuccess());
        assertEquals("MISMATCH", result.getScanResult());
        assertEquals(0, result.getScannedPackageCount());
        assertEquals(RdPutawayTaskStatusEnum.ASSIGNED.getStatus(), task.getStatus());
        verify(taskMapper, never()).updateById(any(RdPutawayTaskDO.class));
        verify(scanRecordMapper).insert(any(RdPutawayScanRecordDO.class));
    }

    @Test
    void shouldReportDriverExceptionAndFreezeTask() {
        RdPutawayTaskDO task = putawayTask(RdPutawayTaskStatusEnum.MOVING, 2, 0, 1);
        RdPutawayTaskLineDO line = putawayLine("PK-001", "PENDING", 0);
        when(taskMapper.selectByIdForUpdate(50L)).thenReturn(task);
        when(taskLineMapper.selectListByTaskIdForUpdate(50L)).thenReturn(List.of(line));
        doAnswer(invocation -> {
            ((RdPutawayExceptionDO) invocation.getArgument(0)).setId(80L);
            return 1;
        }).when(exceptionMapper).insert(any(RdPutawayExceptionDO.class));
        RdPutawayExceptionCreateReqVO reqVO = new RdPutawayExceptionCreateReqVO();
        reqVO.setId(50L);
        reqVO.setExceptionType("LOCATION_OCCUPIED");
        reqVO.setPackageCode("PK-001");
        reqVO.setDescription("计划库位已有其他货物");

        Long id = service.reportException(reqVO, 30L, "司机30");

        assertEquals(80L, id);
        assertEquals(RdPutawayTaskStatusEnum.EXCEPTION_PENDING.getStatus(), task.getStatus());
        assertEquals(1, task.getExceptionPackageCount());
        assertEquals("EXCEPTION", line.getLineStatus());
        verify(exceptionMapper).insert(any(RdPutawayExceptionDO.class));
        verify(taskLineMapper).updateById(line);
        verify(taskMapper).updateById(task);
    }

    @Test
    void shouldRejectDriverScanBeforeReadingTask() {
        assertThrows(RuntimeException.class, () -> service.scanPackage(scanReq("PK-001", "denied"),
                30L, "司机30"));
        verify(taskMapper, never()).selectByIdForUpdate(anyLong());
        verify(scanRecordMapper, never()).insert(any(RdPutawayScanRecordDO.class));
    }

    @Test
    void shouldRejectSingleScanKeyReusedForAnotherPackage() {
        allowKeeperScan();
        when(taskMapper.selectByIdForUpdate(50L)).thenReturn(putawayTask(RdPutawayTaskStatusEnum.MOVING, 2, 1, 1));
        when(scanRecordMapper.selectByTaskAndIdempotencyKey(50L, "scan-001")).thenReturn(
                new RdPutawayScanRecordDO().setOperatorId(20L).setPackageCode("PK-001"));
        assertThrows(RuntimeException.class, () -> service.scanPackage(scanReq("PK-002", "scan-001"),
                20L, "仓管20"));
        verify(taskLineMapper, never()).selectListByTaskIdForUpdate(anyLong());
    }

    private void allowKeeperScan() {
        when(permissionService.hasAnyPermissions(20L, "rd:putaway-task:confirm")).thenReturn(true);
    }

    @Test
    void shouldConfirmPutawayAndCreateOfficialInventoryRecords() {
        RdPutawayTaskDO task = putawayTask(RdPutawayTaskStatusEnum.PENDING_CONFIRMATION, 1, 1, 3)
                .setPlannedLocationId(10L).setPlannedLocationCode("A-01")
                .setTotalWeight(new BigDecimal("600"));
        RdPutawayTaskLineDO line = putawayLine("PK-001", "SCANNED", 1);
        RdWarehouseLocationDO location = availableLocation(10L, "A-01", "10", "10000")
                .setReservedPackages(BigDecimal.ONE)
                .setReservedWeight(new BigDecimal("600"));
        RdPackageUnitDO packageUnit = packageUnit(1L, "PK-001", "600")
                .setStatus(RdPackageStatusEnum.INBOUND_PENDING.getStatus());
        when(taskMapper.selectByIdForUpdate(50L)).thenReturn(task);
        when(exceptionMapper.selectOpenListByTaskIdForUpdate(50L)).thenReturn(List.of());
        when(taskLineMapper.selectListByTaskIdForUpdate(50L)).thenReturn(List.of(line));
        when(locationMapper.selectByIdForUpdate(10L)).thenReturn(location);
        when(packageMapper.selectLocationOccupancySummaryByLocationId(eq(10L), any()))
                .thenReturn(summary(10L, "0", "0"), summary(10L, "1", "600"));
        when(taskMapper.selectLocationReservationSummaryByLocationId(eq(10L), any()))
                .thenReturn(summary(10L, "1", "600"), summary(10L, "0", "0"));
        when(packageMapper.selectByIdForUpdate(1L)).thenReturn(packageUnit);
        when(stockTransactionMapper.selectByIdempotencyKey("PUTAWAY:50:1")).thenReturn(null);
        when(stockLotMapper.selectInboundLotForUpdate(any(), any(), any(), any(), any(), any()))
                .thenReturn(null);
        doAnswer(invocation -> {
            ((RdInboundOrderDO) invocation.getArgument(0)).setId(80L);
            return 1;
        }).when(inboundOrderMapper).insert(any(RdInboundOrderDO.class));
        doAnswer(invocation -> {
            ((RdStockLotDO) invocation.getArgument(0)).setId(90L);
            return 1;
        }).when(stockLotMapper).insert(any(RdStockLotDO.class));
        doAnswer(invocation -> {
            ((RdStockTransactionDO) invocation.getArgument(0)).setId(100L);
            return 1;
        }).when(stockTransactionMapper).insert(any(RdStockTransactionDO.class));
        doAnswer(invocation -> {
            ((RdInboundOrderLineDO) invocation.getArgument(0)).setId(110L);
            return 1;
        }).when(inboundOrderLineMapper).insert(any(RdInboundOrderLineDO.class));
        RdPutawayTaskConfirmReqVO reqVO = new RdPutawayTaskConfirmReqVO();
        reqVO.setId(50L);
        reqVO.setFinalLocationId(10L);
        reqVO.setVersion(3);

        Long orderId = service.confirmTask(reqVO, 20L, "仓管员");

        assertEquals(80L, orderId);
        assertEquals(RdPutawayTaskStatusEnum.PUTAWAY_COMPLETED.getStatus(), task.getStatus());
        assertEquals(10L, task.getFinalLocationId());
        assertEquals(RdPackageStatusEnum.IN_STOCK.getStatus(), packageUnit.getStatus());
        assertEquals(10L, packageUnit.getLocationId());
        assertEquals(90L, packageUnit.getStockLotId());
        assertEquals("CONFIRMED", line.getLineStatus());
        assertEquals(80L, line.getInboundOrderId());
        assertEquals(100L, line.getStockTransactionId());
        assertEquals(BigDecimal.ONE, location.getOccupiedPackages());
        assertEquals(BigDecimal.ZERO, location.getReservedPackages());
        verify(inboundOrderMapper).insert(any(RdInboundOrderDO.class));
        verify(inboundOrderLineMapper).insert(any(RdInboundOrderLineDO.class));
        verify(stockTransactionMapper).insert(any(RdStockTransactionDO.class));
    }

    private void mockDriver(Long id) {
        when(adminUserService.getUser(id)).thenReturn(new AdminUserDO().setId(id)
                .setUsername("driver" + id).setNickname("司机" + id)
                .setStatus(CommonStatusEnum.ENABLE.getStatus()));
        when(permissionService.hasAnyRoles(eq(id), eq("rd_forklift_driver"))).thenReturn(true);
    }

    private static RdWarehouseLocationDO availableLocation(Long id, String code,
                                                           String capacityPackages,
                                                           String capacityWeight) {
        return new RdWarehouseLocationDO().setId(id).setWarehouseCode("WH-01")
                .setWarehouseName("成品库").setAreaCode("A").setAreaName("A区")
                .setLocationCode(code).setCapacityPackages(new BigDecimal(capacityPackages))
                .setCapacityWeight(new BigDecimal(capacityWeight))
                .setOccupiedPackages(BigDecimal.ZERO).setOccupiedWeight(BigDecimal.ZERO)
                .setReservedPackages(BigDecimal.ZERO).setReservedWeight(BigDecimal.ZERO)
                .setEnabled(true).setFrozen(false).setLocationStatus("AVAILABLE").setVersion(0);
    }

    private static RdPackageUnitDO packageUnit(Long id, String code, String weight) {
        return new RdPackageUnitDO().setId(id).setPackageCode(code)
                .setCustomerName("客户A").setProductModel("YG15").setFurnaceNo("1-13#28")
                .setFurnaceLayer("下层").setPackageWeight(new BigDecimal(weight))
                .setStatus(RdPackageStatusEnum.QC_PASSED.getStatus()).setVersion(0);
    }

    private static RdLocationCapacitySummaryDO summary(Long locationId, String packages, String weight) {
        RdLocationCapacitySummaryDO value = new RdLocationCapacitySummaryDO();
        value.setLocationId(locationId);
        value.setPackageCount(new BigDecimal(packages));
        value.setTotalWeight(new BigDecimal(weight));
        return value;
    }

    private static RdPutawayTaskDO putawayTask(RdPutawayTaskStatusEnum status, int packages,
                                                int scanned, int version) {
        return new RdPutawayTaskDO().setId(50L).setTaskNo("SJ-001")
                .setStatus(status.getStatus()).setPackageCount(packages)
                .setScannedPackageCount(scanned).setExceptionPackageCount(0)
                .setForkliftDriverId(30L).setForkliftDriverName("司机30")
                .setTotalWeight(new BigDecimal("600")).setVersion(version);
    }

    private static RdPutawayTaskLineDO putawayLine(String code, String status, int scanCount) {
        return new RdPutawayTaskLineDO().setId(60L).setTaskId(50L).setLineNo(1)
                .setPackageId(1L).setPackageCode(code).setLineStatus(status)
                .setCustomerName("客户A").setProductModel("YG15").setFurnaceNo("1-13#28")
                .setFurnaceLayer("下层").setPackageWeight(new BigDecimal("600"))
                .setScanCount(scanCount).setVersion(0);
    }

    private static RdPutawayScanReqVO scanReq(String packageCode, String idempotencyKey) {
        RdPutawayScanReqVO reqVO = new RdPutawayScanReqVO();
        reqVO.setId(50L);
        reqVO.setPackageCode(packageCode);
        reqVO.setIdempotencyKey(idempotencyKey);
        reqVO.setDeviceCode("PDA-TEST");
        return reqVO;
    }
}
