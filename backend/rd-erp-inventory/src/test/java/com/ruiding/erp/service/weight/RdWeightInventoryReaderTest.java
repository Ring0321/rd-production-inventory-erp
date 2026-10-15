package com.ruiding.erp.service.weight;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingPackageClaimDO;
import com.ruiding.erp.dal.dataobject.masterdata.RdWarehouseLocationDO;
import com.ruiding.erp.dal.dataobject.order.RdOrderFulfillmentDO;
import com.ruiding.erp.dal.dataobject.order.RdSaleOrderDO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockLotDO;
import com.ruiding.erp.dal.mysql.loading.*;
import com.ruiding.erp.dal.mysql.masterdata.RdWarehouseLocationMapper;
import com.ruiding.erp.dal.mysql.order.RdOrderFulfillmentMapper;
import com.ruiding.erp.dal.mysql.order.RdSaleOrderMapper;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.dal.mysql.quality.RdQualityInspectionMapper;
import com.ruiding.erp.dal.mysql.stock.RdStockLotMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static com.ruiding.erp.service.weight.RdWeightAssistantServiceTest.*;
import static com.ruiding.erp.service.weight.RdWeightSolverTest.kg;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RdWeightInventoryReaderTest {
    @Mock RdPickupSlipMapper slipMapper;
    @Mock RdPickupSlipLineMapper slipLineMapper;
    @Mock RdLoadingTaskMapper taskMapper;
    @Mock RdPackageUnitMapper packageMapper;
    @Mock RdStockLotMapper stockLotMapper;
    @Mock RdWarehouseLocationMapper locationMapper;
    @Mock RdLoadingPackageClaimMapper claimMapper;
    @Mock RdQualityInspectionMapper qualityMapper;
    @Mock RdSaleOrderMapper orderMapper;
    @Mock RdOrderFulfillmentMapper fulfillmentMapper;
    @Mock RdLoadingTaskLineMapper taskLineMapper;
    @InjectMocks RdWeightInventoryReader reader;

    @Test void returnsActualBagWithExactCustomerModelAndReviewedQuality() {
        setup();
        var inventory = reader.readCandidates(slip(), line(11));
        assertEquals(1, inventory.bags().size());
        assertEquals("PK-1", inventory.bags().get(0).packageCode());
        assertEquals(0, inventory.bags().get(0).packageWeightKg().compareTo(kg("499.999")));
        assertEquals(3, inventory.lotCaps().get(10L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-customer", "case-customer", "wrong-model", "held", "unknown-status", "unknown-weight",
            "precision", "claimed", "missing-lot", "locked-lot", "unknown-locked", "no-stock", "frozen", "disabled", "unknown-frozen",
            "unknown-location", "stocktake", "bin-mismatch", "qc-missing", "qc-rejected", "qc-pending", "qc-unreviewed", "tenant"})
    void excludesBlockedHeldUnknownOrUnusableRecords(String change) {
        var unit = setup();
        switch (change) {
            case "wrong-customer" -> unit.setCustomerName("OTHER");
            case "case-customer" -> unit.setCustomerName("customer");
            case "wrong-model" -> unit.setProductModel("OTHER");
            case "held" -> unit.setStatus("OUTBOUND_PENDING");
            case "unknown-status" -> unit.setStatus("UNKNOWN");
            case "unknown-weight" -> unit.setPackageWeight(null);
            case "precision" -> unit.setPackageWeight(kg("1.0001"));
            case "claimed" -> when(claimMapper.isClaimed(1L, 1L)).thenReturn(true);
            case "missing-lot" -> when(stockLotMapper.selectById(10L)).thenReturn(null);
            case "locked-lot" -> lot.setStatus("LOCKED");
            case "unknown-locked" -> lot.setLockedQty(null);
            case "no-stock" -> lot.setWarehouseQty(kg("0"));
            case "frozen" -> location.setFrozen(true);
            case "disabled" -> location.setEnabled(false);
            case "unknown-frozen" -> location.setFrozen(null);
            case "unknown-location" -> location.setLocationStatus(null);
            case "stocktake" -> location.setStocktakeOrderId(90L);
            case "bin-mismatch" -> location.setLocationCode("OTHER");
            case "qc-missing" -> when(qualityMapper.selectListByCoveredPackageId(1L)).thenReturn(List.of());
            case "qc-rejected" -> quality.setStatus("REJECTED");
            case "qc-pending" -> quality.setStatus("SUBMITTED");
            case "qc-unreviewed" -> quality.setReviewTime(null);
            case "tenant" -> unit.setTenantId(2L);
            default -> fail(change);
        }
        assertTrue(reader.readCandidates(slip(), line(11)).bags().isEmpty(), change);
        verify(packageMapper, never()).updateById(any(RdPackageUnitDO.class));
        verify(claimMapper, never()).insert(any(RdLoadingPackageClaimDO.class));
    }

    @Test void subtractsRealClaimsAndOtherOrderLocksFromFreeLotCapacity() {
        setup();
        lot.setLockedQty(kg("1"));
        when(packageMapper.selectCount(any(Wrapper.class))).thenReturn(1L, 0L);
        assertEquals(1, reader.readCandidates(slip(), line(11)).lotCaps().get(10L));
    }

    @Test void claimAlreadyCoveredByOrderLockIsNotSubtractedTwice() {
        setup();
        lot.setLockedQty(kg("1"));
        when(packageMapper.selectCount(any(Wrapper.class))).thenReturn(1L, 1L);
        assertEquals(2, reader.readCandidates(slip(), line(11)).lotCaps().get(10L));
    }

    @Test void orderLinkedLineRequiresItsActualAllocationAndSubtractsClaimedFulfillment() {
        setup();
        var line = line(11);
        line.setSaleOrderId(90L);
        var order = new RdSaleOrderDO().setId(90L).setCustomerName("CUSTOMER").setProductModel("MODEL").setStatus("OPEN");
        order.setTenantId(1L);
        when(orderMapper.selectById(90L)).thenReturn(order);
        var allocation = new RdOrderFulfillmentDO().setId(80L).setOrderId(90L).setStockLotId(10L)
                .setStatus("ALLOCATED").setAllocatedQty(kg("3")).setOutboundQty(kg("1"));
        allocation.setTenantId(1L);
        when(fulfillmentMapper.selectActive(90L, 10L)).thenReturn(allocation);
        lot.setLockedQty(kg("2"));
        when(packageMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(taskLineMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        assertEquals(1, reader.readCandidates(slip(), line).lotCaps().get(10L));
        allocation.setStatus("MATCHED");
        assertTrue(reader.readCandidates(slip(), line).bags().isEmpty());
    }

    @Test void unallocatedClaimsCannotBorrowAnotherOrdersReservedCapacity() {
        setup();
        var line = line(11);
        line.setSaleOrderId(90L);
        var order = new RdSaleOrderDO().setId(90L).setCustomerName("CUSTOMER").setProductModel("MODEL").setStatus("OPEN");
        order.setTenantId(1L);
        when(orderMapper.selectById(90L)).thenReturn(order);
        var allocation = new RdOrderFulfillmentDO().setId(80L).setOrderId(90L).setStockLotId(10L)
                .setStatus("ALLOCATED").setAllocatedQty(kg("2")).setOutboundQty(kg("0"));
        allocation.setTenantId(1L);
        when(fulfillmentMapper.selectActive(90L, 10L)).thenReturn(allocation);
        lot.setLockedQty(kg("3"));
        when(packageMapper.selectCount(any(Wrapper.class))).thenReturn(2L, 0L);
        assertTrue(reader.readCandidates(slip(), line).bags().isEmpty());
    }

    @Test void preservesBudgetSignalInsteadOfChoosingFirstFiveHundred() {
        when(packageMapper.selectList(any(Wrapper.class))).thenReturn(java.util.Collections.nCopies(501, new RdPackageUnitDO()));
        var result = reader.readCandidates(slip(), line(11));
        assertEquals(501, result.scannedCount());
        assertFalse(result.complete());
        assertTrue(result.bags().isEmpty());
    }

    private RdStockLotDO lot;
    private RdWarehouseLocationDO location;
    private RdQualityInspectionDO quality;
    private RdPackageUnitDO setup() {
        var unit = new RdPackageUnitDO().setId(1L).setPackageCode("PK-1").setCustomerName("CUSTOMER")
                .setProductModel("MODEL").setStatus("IN_STOCK").setPackageWeight(kg("499.999"))
                .setLocationId(20L).setLocationCode("A-01").setStockLotId(10L).setProductionOutputId(30L);
        unit.setTenantId(1L);
        lot = new RdStockLotDO().setId(10L).setLocationId(20L).setLocationCode("A-01")
                .setCustomerName("CUSTOMER").setProductModel("MODEL").setStatus("AVAILABLE")
                .setWarehouseQty(kg("3")).setLockedQty(kg("0"));
        lot.setTenantId(1L);
        location = new RdWarehouseLocationDO().setId(20L).setLocationCode("A-01")
                .setEnabled(true).setFrozen(false).setLocationStatus("AVAILABLE");
        location.setTenantId(1L);
        quality = new RdQualityInspectionDO().setId(40L).setProductionOutputId(30L)
                .setStatus("PASSED").setConclusion("PASSED").setReviewerId(7L).setReviewTime(LocalDateTime.now());
        quality.setTenantId(1L);
        when(packageMapper.selectList(any(Wrapper.class))).thenReturn(List.of(unit));
        lenient().when(stockLotMapper.selectById(10L)).thenReturn(lot);
        lenient().when(locationMapper.selectById(20L)).thenReturn(location);
        lenient().when(qualityMapper.selectListByCoveredPackageId(1L)).thenReturn(List.of(quality));
        return unit;
    }
}
