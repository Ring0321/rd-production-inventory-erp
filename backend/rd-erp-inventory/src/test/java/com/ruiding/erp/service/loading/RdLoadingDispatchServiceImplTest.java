package com.ruiding.erp.service.loading;

import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import com.ruiding.erp.controller.admin.loading.vo.RdLoadingExceptionCreateReqVO;
import com.ruiding.erp.controller.admin.loading.vo.RdLoadingExceptionResolveReqVO;
import com.ruiding.erp.controller.admin.loading.vo.RdLoadingTaskCreateReqVO;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingExceptionDO;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingPackageClaimDO;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingTaskDO;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingTaskLineDO;
import com.ruiding.erp.dal.dataobject.loading.RdPickupSlipDO;
import com.ruiding.erp.dal.dataobject.loading.RdPickupSlipLineDO;
import com.ruiding.erp.dal.dataobject.masterdata.RdWarehouseLocationDO;
import com.ruiding.erp.dal.dataobject.order.RdOrderFulfillmentDO;
import com.ruiding.erp.dal.dataobject.order.RdSaleOrderDO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockLotDO;
import com.ruiding.erp.dal.mysql.loading.RdLoadingExceptionMapper;
import com.ruiding.erp.dal.mysql.loading.RdLoadingPackageClaimMapper;
import com.ruiding.erp.dal.mysql.loading.RdLoadingTaskLineMapper;
import com.ruiding.erp.dal.mysql.loading.RdLoadingTaskMapper;
import com.ruiding.erp.dal.mysql.loading.RdPickupSlipLineMapper;
import com.ruiding.erp.dal.mysql.loading.RdPickupSlipMapper;
import com.ruiding.erp.dal.mysql.masterdata.RdWarehouseLocationMapper;
import com.ruiding.erp.dal.mysql.order.RdOrderFulfillmentMapper;
import com.ruiding.erp.dal.mysql.order.RdSaleOrderMapper;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.dal.mysql.stock.RdStockLotMapper;
import com.ruiding.erp.enums.RdPackageStatusEnum;
import com.ruiding.erp.enums.RdLoadingTaskStatusEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.AfterEach;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RdLoadingDispatchServiceImplTest {
    @Mock private RdPickupSlipMapper slipMapper;
    @Mock private RdPickupSlipLineMapper slipLineMapper;
    @Mock private RdLoadingTaskMapper taskMapper;
    @Mock private RdLoadingTaskLineMapper taskLineMapper;
    @Mock private com.ruiding.erp.dal.mysql.loading.RdLoadingScanRecordMapper scanRecordMapper;
    @Mock private RdLoadingExceptionMapper exceptionMapper;
    @Mock private RdLoadingPackageClaimMapper claimMapper;
    @Mock private RdPackageUnitMapper packageMapper;
    @Mock private RdStockLotMapper stockLotMapper;
    @Mock private RdWarehouseLocationMapper locationMapper;
    @Mock private RdSaleOrderMapper saleOrderMapper;
    @Mock private RdOrderFulfillmentMapper fulfillmentMapper;
    @Mock private AdminUserService userService;
    @Mock private PermissionService permissionService;
    @Mock private com.ruiding.erp.service.packageunit.RdPackageTaskHistoryService taskHistoryService;
    @InjectMocks private RdLoadingDispatchServiceImpl service;

    @AfterEach
    void clearTenant() { TenantContextHolder.clear(); }

    @Test
    void shouldDispatchOneTaskAcrossTwoLocationsAndModelsWithoutMovingStock() {
        mockSlipAndDriver();
        when(slipLineMapper.selectListBySlipId(1L)).thenReturn(List.of(
                line(11L, "A", "上层", 1), line(12L, "B", "下层", 1)));
        mockPackage(101L, 201L, 301L, "A", "上层");
        mockPackage(102L, 202L, 302L, "B", "下层");
        doAnswer(invocation -> { ((RdLoadingTaskDO) invocation.getArgument(0)).setId(500L); return 1; })
                .when(taskMapper).insert(any(RdLoadingTaskDO.class));
        AtomicLong nextLineId = new AtomicLong(600L);
        doAnswer(invocation -> { ((RdLoadingTaskLineDO) invocation.getArgument(0)).setId(nextLineId.getAndIncrement()); return 1; })
                .when(taskLineMapper).insert(any(RdLoadingTaskLineDO.class));

        Long taskId = service.createTask(request(selection(11L, 101L), selection(12L, 102L)), 20L, "仓管");

        assertEquals(500L, taskId);
        ArgumentCaptor<RdLoadingTaskDO> tasks = ArgumentCaptor.forClass(RdLoadingTaskDO.class);
        verify(taskMapper).insert(tasks.capture());
        assertEquals("ASSIGNED", tasks.getValue().getStatus());
        assertEquals(2, tasks.getValue().getPlannedPackageCount());
        assertEquals(0, tasks.getValue().getScannedPackageCount());
        verify(taskLineMapper, times(2)).insert(any(RdLoadingTaskLineDO.class));
        ArgumentCaptor<RdLoadingPackageClaimDO> claims = ArgumentCaptor.forClass(RdLoadingPackageClaimDO.class);
        verify(claimMapper, times(2)).insert(claims.capture());
        assertEquals(List.of(101L, 102L), claims.getAllValues().stream().map(RdLoadingPackageClaimDO::getPackageId).toList());
        verify(packageMapper, never()).updateById(any(RdPackageUnitDO.class));
        ArgumentCaptor<RdPickupSlipDO> slips = ArgumentCaptor.forClass(RdPickupSlipDO.class);
        verify(slipMapper).updateById(slips.capture());
        assertEquals("DISPATCHED", slips.getValue().getStatus());
        ArgumentCaptor<com.ruiding.erp.dal.dataobject.packageunit.RdPackageTaskEventDO> history =
                ArgumentCaptor.forClass(com.ruiding.erp.dal.dataobject.packageunit.RdPackageTaskEventDO.class);
        verify(taskHistoryService, times(2)).append(history.capture());
        assertEquals(List.of(101L, 102L), history.getAllValues().stream()
                .map(com.ruiding.erp.dal.dataobject.packageunit.RdPackageTaskEventDO::getPackageId).toList());
        assertEquals("ASSIGNED", history.getValue().getEventCode());
    }

    @Test
    void shouldRejectIncompleteSelectionBeforeCreatingTask() {
        mockSlipAndDriver();
        when(slipLineMapper.selectListBySlipId(1L)).thenReturn(List.of(line(11L, "A", "上层", 2)));
        mockPackage(101L, 201L, 301L, "A", "上层");

        assertThrows(RuntimeException.class, () -> service.createTask(request(selection(11L, 101L)), 20L, "仓管"));

        verify(taskMapper, never()).insert(any(RdLoadingTaskDO.class));
        verify(claimMapper, never()).insert(any(RdLoadingPackageClaimDO.class));
    }

    @Test
    void shouldRejectDuplicatePackageSelection() {
        mockSlipAndDriver();
        when(slipLineMapper.selectListBySlipId(1L)).thenReturn(List.of(line(11L, "A", "上层", 2)));
        assertThrows(RuntimeException.class, () -> service.createTask(
                request(selection(11L, 101L), selection(11L, 101L)), 20L, "仓管"));
        verify(packageMapper, never()).selectByIdForUpdate(any());
    }

    @Test
    void shouldRejectNullSelectionBeforeLockingPackages() {
        mockSlipAndDriver();
        when(slipLineMapper.selectListBySlipId(1L)).thenReturn(List.of(line(11L, "A", "上层", 1)));
        RdLoadingTaskCreateReqVO req = request(selection(11L, 101L));
        req.setSelections(Arrays.asList((RdLoadingTaskCreateReqVO.Selection) null));

        assertThrows(RuntimeException.class, () -> service.createTask(req, 20L, "仓管"));

        verify(packageMapper, never()).selectByIdForUpdate(any());
        verify(taskMapper, never()).insert(any(RdLoadingTaskDO.class));
    }

    @Test
    void driverCannotReadAnotherDriversTask() {
        RdLoadingTaskDO task = new RdLoadingTaskDO();
        task.setId(500L);
        task.setForkliftDriverId(30L);
        when(taskMapper.selectById(500L)).thenReturn(task);

        assertThrows(RuntimeException.class, () -> service.getTask(500L, 31L));

        verify(taskLineMapper, never()).selectListByTaskId(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELLED", "OUTBOUNDED"})
    void shouldRejectExceptionForInactivePackageLine(String status) {
        var task = loadingTask(RdLoadingTaskStatusEnum.LOADING, 2, 0);
        var line = loadingLine(600L, "PK-001", status);
        when(taskMapper.selectByIdForUpdate(500L)).thenReturn(task);
        when(taskLineMapper.selectListByTaskIdForUpdate(500L)).thenReturn(List.of(line));
        var req = new RdLoadingExceptionCreateReqVO().setTaskId(500L)
                .setTaskLineId(600L).setPackageCode("PK-001")
                .setExceptionType("WRONG_PACKAGE").setDescription("本地测试非活动关联包");
        var version = task.getVersion();
        var error = assertThrows(ServiceException.class, () -> service.reportException(req, 30L, "司机30"));
        assertEquals(1080530012, error.getCode());
        assertEquals(RdLoadingTaskStatusEnum.LOADING.getStatus(), task.getStatus());
        assertEquals(version, task.getVersion());
        assertEquals(status, line.getLineStatus());
        verify(exceptionMapper, never()).insert(any(RdLoadingExceptionDO.class));
        verify(taskMapper, never()).updateById(any(RdLoadingTaskDO.class));
        verify(taskLineMapper, never()).updateById(any(RdLoadingTaskLineDO.class));
    }

    @Test
    void shouldReportLoadingExceptionAndFreezeTask() {
        RdLoadingTaskDO task = loadingTask(RdLoadingTaskStatusEnum.LOADING, 2, 0);
        RdLoadingTaskLineDO line = loadingLine(600L, "PK-001", "PLANNED");
        when(taskMapper.selectByIdForUpdate(500L)).thenReturn(task);
        when(taskLineMapper.selectListByTaskIdForUpdate(500L)).thenReturn(List.of(line));
        doAnswer(invocation -> {
            ((RdLoadingExceptionDO) invocation.getArgument(0)).setId(80L);
            return 1;
        }).when(exceptionMapper).insert(any(RdLoadingExceptionDO.class));
        RdLoadingExceptionCreateReqVO reqVO = new RdLoadingExceptionCreateReqVO();
        reqVO.setTaskId(500L);
        reqVO.setPackageCode("PK-001");
        reqVO.setExceptionType("LOCATION_MISMATCH");
        reqVO.setDescription("计划库位现场找不到该包");

        Long id = service.reportException(reqVO, 30L, "司机30");

        assertEquals(80L, id);
        assertEquals(RdLoadingTaskStatusEnum.EXCEPTION_PENDING.getStatus(), task.getStatus());
        assertEquals("EXCEPTION", line.getLineStatus());
        verify(exceptionMapper).insert(any(RdLoadingExceptionDO.class));
        verify(taskLineMapper).updateById(line);
        verify(taskMapper).updateById(task);
    }

    @Test
    void shouldResolveLoadingExceptionByReturningTaskToDriver() {
        RdLoadingTaskDO task = loadingTask(RdLoadingTaskStatusEnum.EXCEPTION_PENDING, 2, 1);
        task.setVersion(3);
        RdLoadingTaskLineDO exceptionLine = loadingLine(600L, "PK-001", "EXCEPTION");
        RdLoadingTaskLineDO scannedLine = loadingLine(601L, "PK-002", "SCANNED");
        RdLoadingExceptionDO openException = loadingException();
        when(taskMapper.selectByIdForUpdate(500L)).thenReturn(task);
        when(exceptionMapper.selectOpenListByTaskIdForUpdate(500L)).thenReturn(List.of(openException));
        when(taskLineMapper.selectListByTaskIdForUpdate(500L)).thenReturn(List.of(exceptionLine, scannedLine));
        RdLoadingExceptionResolveReqVO reqVO = new RdLoadingExceptionResolveReqVO();
        reqVO.setTaskId(500L);
        reqVO.setVersion(3);
        reqVO.setResolutionAction("RETURN_TO_DRIVER");
        reqVO.setHandlingResult("仓管已核实，退回司机继续执行");

        service.resolveException(reqVO, 20L, "仓管");

        assertEquals("RESOLVED", openException.getExceptionStatus());
        assertEquals("RETURN_TO_DRIVER", openException.getResolutionAction());
        assertEquals(RdLoadingTaskStatusEnum.ASSIGNED.getStatus(), task.getStatus());
        assertEquals("PLANNED", exceptionLine.getLineStatus());
        assertEquals(1, task.getScannedPackageCount());
        verify(exceptionMapper).updateById(openException);
        verify(taskLineMapper).updateById(exceptionLine);
        verify(taskMapper).updateById(task);
    }

    @Test
    void shouldReplacePackageAndReturnTaskToDriverAfterException() {
        TenantContextHolder.setTenantId(1L);
        RdLoadingTaskDO task = loadingTask(RdLoadingTaskStatusEnum.EXCEPTION_PENDING, 2, 0);
        task.setTenantId(1L);
        task.setCustomerName("客户甲");
        task.setVersion(3);
        RdLoadingTaskLineDO exceptionLine = loadingLine(600L, "PK-OLD", "EXCEPTION");
        exceptionLine.setPickupSlipLineId(11L);
        exceptionLine.setSaleOrderId(3000L);
        exceptionLine.setFulfillmentId(7000L);
        RdLoadingExceptionDO openException = loadingException();
        RdLoadingPackageClaimDO oldClaim = new RdLoadingPackageClaimDO();
        oldClaim.setId(90L);
        oldClaim.setTaskId(500L);
        oldClaim.setTaskLineId(600L);
        RdPackageUnitDO replacementUnit = mockPackage(103L, 203L, 303L, "A", "上层");
        when(packageMapper.selectByPackageCodeForUpdate("PK103")).thenReturn(replacementUnit);
        RdSaleOrderDO order = new RdSaleOrderDO();
        order.setId(3000L);
        order.setCustomerName("客户甲");
        order.setProductModel("A");
        when(saleOrderMapper.selectById(3000L)).thenReturn(order);
        RdOrderFulfillmentDO fulfillment = new RdOrderFulfillmentDO();
        fulfillment.setId(8000L);
        fulfillment.setOrderId(3000L);
        fulfillment.setStockLotId(303L);
        fulfillment.setStatus("ALLOCATED");
        fulfillment.setAllocatedQty(new BigDecimal("10"));
        fulfillment.setOutboundQty(BigDecimal.ZERO);
        when(fulfillmentMapper.selectActive(3000L, 303L)).thenReturn(fulfillment);
        when(fulfillmentMapper.selectByIdForUpdate(8000L)).thenReturn(fulfillment);
        RdPickupSlipDO slip = new RdPickupSlipDO();
        slip.setId(1L); slip.setTenantId(1L); slip.setCustomerName("客户甲"); slip.setStatus("DISPATCHED");
        slip.setRequestedPackageCount(1); slip.setRequestedWeight(new BigDecimal("1000"));
        when(slipMapper.selectByIdForUpdate(1L, 1L)).thenReturn(slip);
        RdPickupSlipLineDO pickupLine = line(11L, "A", "上层", 1);
        pickupLine.setSaleOrderId(3000L); pickupLine.setRequestedWeight(new BigDecimal("1000"));
        when(slipLineMapper.selectListBySlipId(1L)).thenReturn(List.of(pickupLine));
        RdStockLotDO replacementLot = stockLotMapper.selectById(303L);
        replacementLot.setWarehouseQty(new BigDecimal("10")); replacementLot.setLockedQty(new BigDecimal("10"));
        when(stockLotMapper.selectByIdForUpdate(303L)).thenReturn(replacementLot);
        when(taskMapper.selectByIdForUpdate(500L)).thenReturn(task);
        when(exceptionMapper.selectOpenListByTaskIdForUpdate(500L)).thenReturn(List.of(openException));
        when(taskLineMapper.selectListByTaskIdForUpdate(500L)).thenReturn(List.of(exceptionLine));
        when(claimMapper.selectByPackageIdForUpdate(103L)).thenReturn(null);
        when(claimMapper.selectByPackageIdForUpdate(exceptionLine.getPackageId())).thenReturn(oldClaim);
        RdLoadingExceptionResolveReqVO reqVO = new RdLoadingExceptionResolveReqVO();
        reqVO.setTaskId(500L);
        reqVO.setVersion(3);
        reqVO.setResolutionAction("REPLACE_PACKAGE");
        reqVO.setHandlingResult("仓管改用同客户同型号替换包，退回司机继续装车");
        RdLoadingExceptionResolveReqVO.Replacement replacement = new RdLoadingExceptionResolveReqVO.Replacement();
        replacement.setTaskLineId(600L);
        replacement.setNewPackageCode("https://id.tjldsic.com/pkg/PK103");
        reqVO.setReplacements(List.of(replacement));

        service.resolveException(reqVO, 20L, "仓管");

        assertEquals("RESOLVED", openException.getExceptionStatus());
        assertEquals("REPLACE_PACKAGE", openException.getResolutionAction());
        assertEquals(RdLoadingTaskStatusEnum.ASSIGNED.getStatus(), task.getStatus());
        assertEquals(103L, exceptionLine.getPackageId());
        assertEquals("PK103", exceptionLine.getPackageCode());
        assertEquals(8000L, exceptionLine.getFulfillmentId());
        assertEquals("PLANNED", exceptionLine.getLineStatus());
        verify(claimMapper).deleteById(90L);
        ArgumentCaptor<RdLoadingPackageClaimDO> claims = ArgumentCaptor.forClass(RdLoadingPackageClaimDO.class);
        verify(claimMapper).insert(claims.capture());
        assertEquals(103L, claims.getValue().getPackageId());
        assertEquals(600L, claims.getValue().getTaskLineId());
        verify(taskLineMapper).updateById(exceptionLine);
        verify(taskMapper).updateById(task);
        ArgumentCaptor<com.ruiding.erp.dal.dataobject.packageunit.RdPackageTaskEventDO> history =
                ArgumentCaptor.forClass(com.ruiding.erp.dal.dataobject.packageunit.RdPackageTaskEventDO.class);
        verify(taskHistoryService, times(4)).append(history.capture());
        var oldEvent = history.getAllValues().stream().filter(item -> "REPLACED_OUT".equals(item.getEventCode()))
                .findFirst().orElseThrow();
        var newEvent = history.getAllValues().stream().filter(item -> "REPLACED_IN".equals(item.getEventCode()))
                .findFirst().orElseThrow();
        assertEquals("PK-OLD", oldEvent.getPackageCode());
        assertEquals(1600L, oldEvent.getPackageId());
        assertEquals("PK103", oldEvent.getRelatedPackageCode());
        assertEquals("PK103", newEvent.getPackageCode());
        assertEquals(103L, newEvent.getPackageId());
        assertEquals("PK-OLD", newEvent.getRelatedPackageCode());
        assertEquals(oldEvent.getEventTime(), newEvent.getEventTime());
    }

    @Test
    void shouldRecordActualSelectedBagsAndNotDuplicateHistoryOnReplay() {
        RdLoadingTaskDO task = loadingTask(RdLoadingTaskStatusEnum.ASSIGNED, 2, 0);
        RdLoadingTaskLineDO selected = loadingLine(600L, "PK-001", "PLANNED");
        RdLoadingTaskLineDO remaining = loadingLine(601L, "PK-002", "PLANNED");
        when(taskMapper.selectByIdForUpdate(500L)).thenReturn(task);
        when(taskLineMapper.selectListByTaskIdForUpdate(500L)).thenReturn(List.of(selected, remaining));
        doAnswer(invocation -> {
            ((com.ruiding.erp.dal.dataobject.loading.RdLoadingScanRecordDO) invocation.getArgument(0)).setId(88L);
            return 1;
        }).when(scanRecordMapper).insert(any(com.ruiding.erp.dal.dataobject.loading.RdLoadingScanRecordDO.class));
        var req = new com.ruiding.erp.controller.admin.loading.vo.RdLoadingBatchScanConfirmReqVO();
        req.setTaskId(500L);
        req.setVersion(0);
        req.setRepresentativePackageCode("PK-002");
        req.setTaskLineIds(List.of(600L));
        req.setIdempotencyKey("test-partial");

        service.confirmBatchScan(req, 20L, "warehouse");

        ArgumentCaptor<com.ruiding.erp.dal.dataobject.packageunit.RdPackageTaskEventDO> history =
                ArgumentCaptor.forClass(com.ruiding.erp.dal.dataobject.packageunit.RdPackageTaskEventDO.class);
        verify(taskHistoryService).append(history.capture());
        assertEquals("PK-001", history.getValue().getPackageCode());
        assertEquals("SCAN_CONFIRMED", history.getValue().getEventCode());
        assertEquals(88L, history.getValue().getSourceId());
        assertEquals("PLANNED", remaining.getLineStatus());
        ArgumentCaptor<com.ruiding.erp.dal.dataobject.loading.RdLoadingScanRecordDO> records =
                ArgumentCaptor.forClass(com.ruiding.erp.dal.dataobject.loading.RdLoadingScanRecordDO.class);
        verify(scanRecordMapper).insert(records.capture());
        var replay = records.getValue();
        when(scanRecordMapper.selectByTaskAndIdempotencyKey(500L, "test-partial")).thenReturn(replay);
        when(taskLineMapper.selectListByTaskId(500L)).thenReturn(List.of(selected, remaining));

        assertEquals(true, service.confirmBatchScan(req, 20L, "warehouse").getReplayed());
        verify(taskHistoryService, times(1)).append(any());
    }

    @Test
    void shouldCancelLoadingTaskAndReleaseClaimsAfterException() {
        TenantContextHolder.setTenantId(1L);
        RdLoadingTaskDO task = loadingTask(RdLoadingTaskStatusEnum.EXCEPTION_PENDING, 2, 1);
        task.setVersion(3);
        RdLoadingTaskLineDO plannedLine = loadingLine(600L, "PK-001", "PLANNED");
        RdLoadingTaskLineDO scannedLine = loadingLine(601L, "PK-002", "SCANNED");
        RdLoadingExceptionDO openException = loadingException();
        RdLoadingPackageClaimDO claim = new RdLoadingPackageClaimDO();
        claim.setId(90L);
        claim.setTaskId(500L);
        RdPickupSlipDO slip = new RdPickupSlipDO();
        slip.setId(1L);
        slip.setStatus("DISPATCHED");
        slip.setVersion(1);
        when(taskMapper.selectByIdForUpdate(500L)).thenReturn(task);
        when(exceptionMapper.selectOpenListByTaskIdForUpdate(500L)).thenReturn(List.of(openException));
        when(taskLineMapper.selectListByTaskIdForUpdate(500L)).thenReturn(List.of(plannedLine, scannedLine));
        when(claimMapper.selectListByTaskIdForUpdate(500L)).thenReturn(List.of(claim));
        when(slipMapper.selectByIdForUpdate(1L, 1L)).thenReturn(slip);
        RdLoadingExceptionResolveReqVO reqVO = new RdLoadingExceptionResolveReqVO();
        reqVO.setTaskId(500L);
        reqVO.setVersion(3);
        reqVO.setResolutionAction("CANCEL");
        reqVO.setHandlingResult("现场无法完成装车，取消后重新派单");

        service.resolveException(reqVO, 20L, "仓管");

        assertEquals("RESOLVED", openException.getExceptionStatus());
        assertEquals("CANCEL", openException.getResolutionAction());
        assertEquals(RdLoadingTaskStatusEnum.CANCELLED.getStatus(), task.getStatus());
        assertEquals("CANCELLED", plannedLine.getLineStatus());
        assertEquals("CANCELLED", scannedLine.getLineStatus());
        assertEquals("ISSUED", slip.getStatus());
        verify(claimMapper).deleteById(90L);
        verify(slipMapper).updateById(slip);
    }

    private void mockSlipAndDriver() {
        TenantContextHolder.setTenantId(1L);
        RdPickupSlipDO slip = new RdPickupSlipDO();
        slip.setId(1L);
        slip.setTenantId(1L);
        slip.setSlipNo("PICK-01");
        slip.setStatus("ISSUED");
        slip.setCustomerName("客户甲");
        when(slipMapper.selectByIdForUpdate(1L, 1L)).thenReturn(slip);
        when(taskMapper.selectCount(any())).thenReturn(0L);
        AdminUserDO driver = new AdminUserDO();
        driver.setId(30L);
        driver.setStatus(0);
        driver.setNickname("司机甲");
        when(userService.getUser(30L)).thenReturn(driver);
        when(permissionService.hasAnyRoles(30L, "rd_forklift_driver")).thenReturn(true);
    }

    private RdPackageUnitDO mockPackage(Long id, Long locationId, Long lotId, String model, String layer) {
        RdPackageUnitDO unit = new RdPackageUnitDO();
        unit.setId(id);
        unit.setTenantId(1L);
        unit.setPackageCode("PK" + id);
        unit.setStatus(RdPackageStatusEnum.IN_STOCK.getStatus());
        unit.setCustomerName("客户甲");
        unit.setProductModel(model);
        unit.setFurnaceNo("1-01");
        unit.setFurnaceLayer(layer);
        unit.setLocationId(locationId);
        unit.setLocationCode("L" + locationId);
        unit.setStockLotId(lotId);
        unit.setPackageWeight(new BigDecimal("1000"));
        lenient().when(packageMapper.selectByIdForUpdate(id)).thenReturn(unit);
        RdWarehouseLocationDO location = new RdWarehouseLocationDO();
        location.setId(locationId);
        location.setLocationCode(unit.getLocationCode());
        location.setEnabled(true);
        location.setFrozen(false);
        when(locationMapper.selectById(locationId)).thenReturn(location);
        RdStockLotDO lot = new RdStockLotDO();
        lot.setId(lotId);
        lot.setLocationId(locationId);
        lot.setCustomerName(unit.getCustomerName());
        lot.setProductModel(model);
        lot.setFurnaceNo(unit.getFurnaceNo());
        lot.setFurnaceLayer(layer);
        when(stockLotMapper.selectById(lotId)).thenReturn(lot);
        return unit;
    }

    private static RdPickupSlipLineDO line(Long id, String model, String layer, int count) {
        RdPickupSlipLineDO line = new RdPickupSlipLineDO();
        line.setId(id);
        line.setProductModel(model);
        line.setFurnaceLayer(layer);
        line.setRequestedPackageCount(count);
        return line;
    }

    private static RdLoadingTaskCreateReqVO.Selection selection(Long lineId, Long packageId) {
        RdLoadingTaskCreateReqVO.Selection selection = new RdLoadingTaskCreateReqVO.Selection();
        selection.setPickupSlipLineId(lineId);
        selection.setPackageId(packageId);
        return selection;
    }

    private static RdLoadingTaskCreateReqVO request(RdLoadingTaskCreateReqVO.Selection... selections) {
        RdLoadingTaskCreateReqVO req = new RdLoadingTaskCreateReqVO();
        req.setPickupSlipId(1L);
        req.setForkliftDriverId(30L);
        req.setSelections(List.of(selections));
        return req;
    }

    private static RdLoadingTaskDO loadingTask(RdLoadingTaskStatusEnum status, int plannedCount, int scannedCount) {
        RdLoadingTaskDO task = new RdLoadingTaskDO();
        task.setId(500L);
        task.setTaskNo("LD-001");
        task.setPickupSlipId(1L);
        task.setStatus(status.getStatus());
        task.setForkliftDriverId(30L);
        task.setPlannedPackageCount(plannedCount);
        task.setScannedPackageCount(scannedCount);
        task.setPlannedWeight(new BigDecimal("2000"));
        task.setScannedWeight(new BigDecimal(scannedCount * 1000));
        task.setPlanVersion(1);
        task.setVersion(0);
        return task;
    }

    private static RdLoadingTaskLineDO loadingLine(Long id, String packageCode, String status) {
        RdLoadingTaskLineDO line = new RdLoadingTaskLineDO();
        line.setId(id);
        line.setTaskId(500L);
        line.setLineNo(id.intValue());
        line.setPackageId(id + 1000);
        line.setPackageCode(packageCode);
        line.setCustomerName("客户甲");
        line.setProductModel("A");
        line.setFurnaceNo("1-01");
        line.setFurnaceLayer("上层");
        line.setStockLotId(301L);
        line.setPlannedLocationId(201L);
        line.setPlannedLocationCode("L201");
        line.setLineStatus(status);
        line.setPackageWeight(new BigDecimal("1000"));
        line.setVersion(0);
        return line;
    }

    private static RdLoadingExceptionDO loadingException() {
        RdLoadingExceptionDO item = new RdLoadingExceptionDO();
        item.setId(80L);
        item.setTaskId(500L);
        item.setExceptionStatus("OPEN");
        item.setVersion(0);
        return item;
    }
}
