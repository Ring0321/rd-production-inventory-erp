package com.ruiding.erp.service.putaway;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayLocationMapRespVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayTaskCreateReqVO;
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

@ExtendWith(MockitoExtension.class)
class RdPutawayTaskServiceImplTest {
    @Mock private RdPackageUnitMapper packageMapper;
    @Mock private RdWarehouseLocationMapper locationMapper;
    @Mock private RdPutawayTaskMapper taskMapper;
    @Mock private RdPutawayTaskLineMapper taskLineMapper;
    @Mock private RdPutawayExceptionMapper exceptionMapper;
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
}
