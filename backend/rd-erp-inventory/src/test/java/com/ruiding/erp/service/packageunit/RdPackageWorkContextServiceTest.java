package com.ruiding.erp.service.packageunit;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingTaskDO;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingTaskLineDO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayTaskDO;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayTaskLineDO;
import com.ruiding.erp.dal.mysql.loading.RdLoadingTaskLineMapper;
import com.ruiding.erp.dal.mysql.loading.RdLoadingTaskMapper;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.dal.mysql.putaway.RdPutawayTaskLineMapper;
import com.ruiding.erp.dal.mysql.putaway.RdPutawayTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RdPackageWorkContextServiceTest {
    @Mock private RdPackageUnitMapper packageMapper;
    @Mock private RdPutawayTaskLineMapper putawayLineMapper;
    @Mock private RdPutawayTaskMapper putawayTaskMapper;
    @Mock private RdLoadingTaskLineMapper loadingLineMapper;
    @Mock private RdLoadingTaskMapper loadingTaskMapper;
    @Mock private PermissionApi permissionApi;
    @InjectMocks private RdPackageWorkContextService service;

    @BeforeEach
    void defaultPermissionChecksToFalse() {
        when(permissionApi.hasAnyPermissions(anyLong(), anyString())).thenReturn(false);
    }

    @Test
    void shouldReturnAssignedLoadingTaskEvenWhenPackageIsStillInStock() {
        mockLoadingTask(1L);
        when(permissionApi.hasAnyPermissions(2L, "rd:loading-task:query")).thenReturn(true);

        var result = service.getContext("PK1", 2L);

        assertEquals(1, result.getTasks().size());
        assertEquals(100L, result.getTasks().get(0).getTaskId());
        assertEquals("LOADING", result.getTasks().get(0).getType());
        assertFalse(result.getTasks().get(0).getMyTask());
        verify(packageMapper, never()).updateById(any(RdPackageUnitDO.class));
    }

    @Test
    void shouldOnlyExposeTasksAssignedToTheDriver() {
        mockLoadingTask(1L);
        when(permissionApi.hasAnyPermissions(1L, "rd:loading-task:execute")).thenReturn(true);
        when(permissionApi.hasAnyPermissions(2L, "rd:loading-task:execute")).thenReturn(true);

        assertTrue(service.getContext("PK1", 1L).getTasks().get(0).getMyTask());
        assertTrue(service.getContext("PK1", 2L).getTasks().isEmpty());
    }

    @Test
    void shouldNotQueryWarehouseTasksWithoutTaskPermissions() {
        when(packageMapper.selectByPackageCode("PK1")).thenReturn(new RdPackageUnitDO().setPackageCode("PK1"));

        assertTrue(service.getContext("PK1", 1L).getTasks().isEmpty());

        verifyNoInteractions(putawayLineMapper, loadingLineMapper, putawayTaskMapper, loadingTaskMapper);
    }

    @Test
    void shouldDropTaskIfItCompletedAfterLineLookup() {
        mockLoadingTask(1L);
        when(permissionApi.hasAnyPermissions(1L, "rd:loading-task:execute")).thenReturn(true);
        when(loadingTaskMapper.selectById(100L)).thenReturn(new RdLoadingTaskDO()
                .setId(100L).setForkliftDriverId(1L).setStatus("OUTBOUNDED"));

        assertTrue(service.getContext("PK1", 1L).getTasks().isEmpty());
    }

    @Test
    void shouldOnlyExposePickupReferenceToSalesWithoutWarehouseTaskAccess() {
        mockLoadingTask(1L);
        when(permissionApi.hasAnyPermissions(2L, "rd:pickup-slip:query")).thenReturn(true);

        var result = service.getContext("PK1", 2L);

        assertTrue(result.getTasks().isEmpty());
        assertEquals(50L, result.getPickupSlipId());
        assertEquals("SLIP-TEST", result.getPickupSlipNo());
    }

    @Test
    void shouldResolvePutawayTaskForAssignedDriver() {
        mockPutawayTask();
        when(permissionApi.hasAnyPermissions(1L, "rd:putaway-task:execute")).thenReturn(true);
        when(permissionApi.hasAnyPermissions(2L, "rd:putaway-task:execute")).thenReturn(true);

        var task = service.getContext("PK1", 1L).getTasks().get(0);

        assertEquals("PUTAWAY", task.getType());
        assertEquals(200L, task.getTaskId());
        assertTrue(task.getMyTask());
        assertTrue(service.getContext("PK1", 2L).getTasks().isEmpty());
    }

    @Test
    void shouldLetWarehouseViewPutawayWithoutPretendingItIsTheirDriverTask() {
        mockPutawayTask();
        when(permissionApi.hasAnyPermissions(2L, "rd:putaway-task:query")).thenReturn(true);

        var task = service.getContext("PK1", 2L).getTasks().get(0);

        assertEquals("PUTAWAY", task.getType());
        assertFalse(task.getMyTask());
    }

    private void mockPutawayTask() {
        when(packageMapper.selectByPackageCode("PK1")).thenReturn(new RdPackageUnitDO()
                .setPackageCode("PK1").setStatus("INBOUND_PENDING"));
        when(putawayLineMapper.selectActiveByPackageCode("PK1")).thenReturn(new RdPutawayTaskLineDO()
                .setId(20L).setTaskId(200L).setPackageCode("PK1"));
        when(putawayTaskMapper.selectById(200L)).thenReturn(new RdPutawayTaskDO()
                .setId(200L).setTaskNo("PUT-TEST").setForkliftDriverId(1L).setStatus("ASSIGNED"));
    }

    private void mockLoadingTask(Long driverId) {
        when(packageMapper.selectByPackageCode("PK1")).thenReturn(new RdPackageUnitDO()
                .setPackageCode("PK1").setStatus("IN_STOCK"));
        when(loadingLineMapper.selectActiveByPackageCode("PK1")).thenReturn(new RdLoadingTaskLineDO()
                .setId(10L).setTaskId(100L).setPackageCode("PK1"));
        when(loadingTaskMapper.selectById(100L)).thenReturn(new RdLoadingTaskDO()
                .setId(100L).setTaskNo("LOAD-TEST").setForkliftDriverId(driverId).setStatus("ASSIGNED")
                .setPickupSlipId(50L).setPickupSlipNo("SLIP-TEST"));
    }
}
