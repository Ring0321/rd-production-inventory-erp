package com.ruiding.erp.service.loading;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.ruiding.erp.controller.admin.loading.vo.RdLoadingExceptionResolveReqVO;
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
import com.ruiding.erp.service.packageunit.RdPackageTaskHistoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.ruiding.erp.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RdLoadingReplacementPlanTest {
    @Mock private RdPickupSlipMapper slipMapper;
    @Mock private RdPickupSlipLineMapper slipLineMapper;
    @Mock private RdLoadingTaskMapper taskMapper;
    @Mock private RdLoadingTaskLineMapper taskLineMapper;
    @Mock private RdLoadingExceptionMapper exceptionMapper;
    @Mock private RdLoadingPackageClaimMapper claimMapper;
    @Mock private RdPackageUnitMapper packageMapper;
    @Mock private RdStockLotMapper stockLotMapper;
    @Mock private RdWarehouseLocationMapper locationMapper;
    @Mock private RdSaleOrderMapper saleOrderMapper;
    @Mock private RdOrderFulfillmentMapper fulfillmentMapper;
    @Mock private RdPackageTaskHistoryService taskHistoryService;
    @InjectMocks private RdLoadingDispatchServiceImpl service;

    private RdLoadingTaskDO task;
    private RdLoadingExceptionDO openException;
    private RdPickupSlipDO slip;
    private RdPickupSlipLineDO pickupLine;
    private RdStockLotDO lot;
    private RdOrderFulfillmentDO fulfillment;
    private final List<RdLoadingTaskLineDO> lines = new ArrayList<>();
    private final List<RdPackageUnitDO> units = new ArrayList<>();

    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    private RdLoadingExceptionResolveReqVO fixture(int count, int replacements) {
        TenantContextHolder.setTenantId(1L);
        task = new RdLoadingTaskDO();
        task.setId(500L); task.setTaskNo("LOCAL-BULK"); task.setTenantId(1L);
        task.setPickupSlipId(1L); task.setCustomerName("客户甲"); task.setStatus("EXCEPTION_PENDING");
        task.setVersion(4); task.setPlanVersion(2); task.setPlannedPackageCount(count);
        task.setScannedPackageCount(0); task.setPlannedWeight(BigDecimal.valueOf(count * 1000L));
        openException = new RdLoadingExceptionDO();
        openException.setId(80L); openException.setTaskId(500L); openException.setExceptionStatus("OPEN"); openException.setVersion(0);
        lenient().when(taskMapper.selectByIdForUpdate(500L)).thenReturn(task);
        lenient().when(exceptionMapper.selectOpenListByTaskIdForUpdate(500L)).thenReturn(List.of(openException));
        lenient().when(taskLineMapper.selectListByTaskIdForUpdate(500L)).thenReturn(lines);
        slip = new RdPickupSlipDO();
        slip.setId(1L); slip.setTenantId(1L); slip.setCustomerName("客户甲"); slip.setStatus("DISPATCHED");
        slip.setRequestedPackageCount(count); slip.setRequestedWeight(BigDecimal.valueOf(count * 1000L));
        lenient().when(slipMapper.selectByIdForUpdate(1L, 1L)).thenReturn(slip);
        pickupLine = new RdPickupSlipLineDO();
        pickupLine.setId(11L); pickupLine.setSaleOrderId(3000L); pickupLine.setProductModel("A");
        pickupLine.setFurnaceNo("1-01"); pickupLine.setFurnaceLayer("上层");
        pickupLine.setRequestedPackageCount(count); pickupLine.setRequestedWeight(slip.getRequestedWeight());
        lenient().when(slipLineMapper.selectListBySlipId(1L)).thenReturn(List.of(pickupLine));
        lot = new RdStockLotDO().setId(301L).setLocationId(201L).setCustomerName("客户甲")
                .setProductModel("A").setFurnaceNo("1-01").setFurnaceLayer("上层")
                .setWarehouseQty(BigDecimal.valueOf(count)).setLockedQty(BigDecimal.valueOf(count));
        lenient().when(stockLotMapper.selectById(301L)).thenReturn(lot);
        lenient().when(stockLotMapper.selectByIdForUpdate(301L)).thenReturn(lot);
        RdWarehouseLocationDO location = new RdWarehouseLocationDO();
        location.setId(201L); location.setLocationCode("L201"); location.setEnabled(true); location.setFrozen(false);
        lenient().when(locationMapper.selectById(201L)).thenReturn(location);
        RdSaleOrderDO order = new RdSaleOrderDO();
        order.setId(3000L); order.setCustomerName("客户甲"); order.setProductModel("A");
        lenient().when(saleOrderMapper.selectById(3000L)).thenReturn(order);
        fulfillment = new RdOrderFulfillmentDO();
        fulfillment.setId(8000L); fulfillment.setOrderId(3000L); fulfillment.setStockLotId(301L);
        fulfillment.setStatus("ALLOCATED"); fulfillment.setAllocatedQty(BigDecimal.valueOf(count)); fulfillment.setOutboundQty(BigDecimal.ZERO);
        lenient().when(fulfillmentMapper.selectActive(3000L, 301L)).thenReturn(fulfillment);
        lenient().when(fulfillmentMapper.selectByIdForUpdate(8000L)).thenReturn(fulfillment);
        var entries = new ArrayList<RdLoadingExceptionResolveReqVO.Replacement>();
        for (int index = 0; index < count; index++) {
            RdLoadingTaskLineDO line = new RdLoadingTaskLineDO();
            line.setId(600L + index); line.setTaskId(500L); line.setLineNo(index + 1);
            line.setPickupSlipLineId(11L); line.setSaleOrderId(3000L); line.setFulfillmentId(8000L);
            line.setPackageId(1600L + index); line.setPackageCode("OLD-" + index);
            line.setStockLotId(301L); line.setCustomerName("客户甲"); line.setProductModel("A");
            line.setFurnaceNo("1-01"); line.setFurnaceLayer("上层"); line.setPackageWeight(new BigDecimal("1000"));
            line.setPlannedLocationId(201L); line.setPlannedLocationCode("L201"); line.setLineStatus("PLANNED"); line.setVersion(0);
            lines.add(line);
            RdLoadingPackageClaimDO claim = new RdLoadingPackageClaimDO();
            claim.setId(90L + index); claim.setTaskId(500L); claim.setTaskLineId(line.getId()); claim.setPackageId(line.getPackageId());
            lenient().when(claimMapper.selectByPackageIdForUpdate(line.getPackageId())).thenReturn(claim);
            if (index < replacements) {
                RdPackageUnitDO unit = new RdPackageUnitDO();
                unit.setId(101L + index); unit.setTenantId(1L); unit.setPackageCode("NEW-" + index); unit.setStatus("IN_STOCK");
                unit.setStockLotId(301L); unit.setCustomerName("客户甲"); unit.setProductModel("A"); unit.setFurnaceNo("1-01"); unit.setFurnaceLayer("上层");
                unit.setLocationId(201L); unit.setLocationCode("L201"); unit.setPackageWeight(new BigDecimal("1000"));
                units.add(unit);
                lenient().when(packageMapper.selectByPackageCodeForUpdate(unit.getPackageCode())).thenReturn(unit);
                var entry = new RdLoadingExceptionResolveReqVO.Replacement();
                entry.setTaskLineId(line.getId()); entry.setNewPackageCode(unit.getPackageCode()); entries.add(entry);
            }
        }
        RdLoadingExceptionResolveReqVO req = new RdLoadingExceptionResolveReqVO();
        req.setTaskId(500L); req.setVersion(4); req.setResolutionAction("REPLACE_PACKAGE"); req.setHandlingResult("本地核定整份换货计划，非现场数据"); req.setReplacements(entries);
        return req;
    }

    private void noDomainWrites() {
        assertEquals("OPEN", openException.getExceptionStatus());
        assertEquals(4, task.getVersion());
        verify(exceptionMapper, never()).updateById(any(RdLoadingExceptionDO.class));
        verify(claimMapper, never()).deleteById(any(Long.class));
        verify(claimMapper, never()).insert(any(RdLoadingPackageClaimDO.class));
        verify(taskLineMapper, never()).updateById(any(RdLoadingTaskLineDO.class));
        verify(taskMapper, never()).updateById(any(RdLoadingTaskDO.class));
        verifyNoInteractions(taskHistoryService);
    }

    @Test void shouldCheckTwoReplacementsTogetherBeforeAnyWrites() {
        var req = fixture(2, 2); fulfillment.setAllocatedQty(BigDecimal.ONE);
        assertEquals(LOADING_FULFILLMENT_INSUFFICIENT.getCode(), assertThrows(ServiceException.class, () -> service.resolveException(req, 20L, "仓管")).getCode());
        noDomainWrites();
    }

    @Test void shouldIncludeRetainedScannedLineInFulfillmentDemand() {
        var req = fixture(2, 1); lines.get(1).setLineStatus("SCANNED"); fulfillment.setAllocatedQty(BigDecimal.ONE);
        assertEquals(LOADING_FULFILLMENT_INSUFFICIENT.getCode(), assertThrows(ServiceException.class, () -> service.resolveException(req, 20L, "仓管")).getCode());
        noDomainWrites();
    }

    @Test void shouldRejectChangedApprovedPickupWeight() {
        var req = fixture(2, 2); units.get(1).setPackageWeight(new BigDecimal("800"));
        assertEquals(LOADING_QUANTITY_MISMATCH.getCode(), assertThrows(ServiceException.class, () -> service.resolveException(req, 20L, "仓管")).getCode());
        noDomainWrites();
    }

    @Test void shouldRejectInsufficientSharedStockLedger() {
        var req = fixture(2, 2); lot.setWarehouseQty(BigDecimal.ONE);
        assertEquals(LOADING_FULFILLMENT_INSUFFICIENT.getCode(), assertThrows(ServiceException.class, () -> service.resolveException(req, 20L, "仓管")).getCode());
        noDomainWrites();
    }

    @Test void shouldReplaceTwoBagsAndPreserveRetainedScanProgress() {
        var req = fixture(3, 2); lines.get(2).setLineStatus("SCANNED");
        service.resolveException(req, 20L, "仓管");
        assertEquals("ASSIGNED", task.getStatus()); assertEquals(5, task.getVersion()); assertEquals(3, task.getPlanVersion());
        assertEquals(3, task.getPlannedPackageCount()); assertEquals(1, task.getScannedPackageCount());
        assertEquals(new BigDecimal("3000"), task.getPlannedWeight());
        assertEquals("NEW-0", lines.get(0).getPackageCode()); assertEquals("NEW-1", lines.get(1).getPackageCode());
        assertEquals("SCANNED", lines.get(2).getLineStatus()); assertEquals("RESOLVED", openException.getExceptionStatus());
        verify(claimMapper, times(2)).insert(any(RdLoadingPackageClaimDO.class));
        verify(taskMapper).updateById(task);
        verify(packageMapper, never()).updateById(any(RdPackageUnitDO.class));
    }

    @Test void shouldAllowDifferentIndividualWeightsWhenApprovedAggregateIsUnchanged() {
        var req = fixture(2, 2); units.get(0).setPackageWeight(new BigDecimal("1200")); units.get(1).setPackageWeight(new BigDecimal("800"));
        service.resolveException(req, 20L, "仓管");
        assertEquals(new BigDecimal("2000"), task.getPlannedWeight());
    }

    @Test void shouldRejectSecondInvalidPairWithoutClosingExceptionOrChangingFirstBag() {
        var req = fixture(2, 2); units.get(1).setFurnaceLayer("下层");
        assertThrows(ServiceException.class, () -> service.resolveException(req, 20L, "仓管")); noDomainWrites();
        assertEquals("OLD-0", lines.get(0).getPackageCode());
    }

    @ParameterizedTest @ValueSource(strings = {"/pkg/", "?x", "#x"})
    void shouldRejectNormalizedEmptyCodeWithoutWrites(String code) {
        var req = fixture(1, 1); req.getReplacements().get(0).setNewPackageCode(code);
        assertThrows(ServiceException.class, () -> service.resolveException(req, 20L, "仓管")); noDomainWrites();
    }

    @Test void shouldRejectMoreThanOneHundredReplacementsBeforeWrites() {
        var req = fixture(1, 1); req.setReplacements(Collections.nCopies(101, req.getReplacements().get(0)));
        assertThrows(ServiceException.class, () -> service.resolveException(req, 20L, "仓管")); noDomainWrites();
    }
}
