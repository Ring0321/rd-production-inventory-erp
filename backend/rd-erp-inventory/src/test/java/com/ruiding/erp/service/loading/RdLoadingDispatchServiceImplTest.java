package com.ruiding.erp.service.loading;

import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import com.ruiding.erp.controller.admin.loading.vo.RdLoadingTaskCreateReqVO;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingPackageClaimDO;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingTaskDO;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingTaskLineDO;
import com.ruiding.erp.dal.dataobject.loading.RdPickupSlipDO;
import com.ruiding.erp.dal.dataobject.loading.RdPickupSlipLineDO;
import com.ruiding.erp.dal.dataobject.masterdata.RdWarehouseLocationDO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockLotDO;
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
import org.junit.jupiter.api.Test;
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
    @Mock private RdLoadingPackageClaimMapper claimMapper;
    @Mock private RdPackageUnitMapper packageMapper;
    @Mock private RdStockLotMapper stockLotMapper;
    @Mock private RdWarehouseLocationMapper locationMapper;
    @Mock private RdSaleOrderMapper saleOrderMapper;
    @Mock private RdOrderFulfillmentMapper fulfillmentMapper;
    @Mock private AdminUserService userService;
    @Mock private PermissionService permissionService;
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

    private void mockPackage(Long id, Long locationId, Long lotId, String model, String layer) {
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
        when(packageMapper.selectByIdForUpdate(id)).thenReturn(unit);
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
}
