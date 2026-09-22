package com.ruiding.erp.service.transfer;

import com.ruiding.erp.dal.dataobject.order.RdSaleOrderDO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockLotDO;
import com.ruiding.erp.dal.dataobject.transfer.RdTransferOrderDO;
import com.ruiding.erp.dal.dataobject.transfer.RdTransferOrderPackageDO;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.dal.mysql.loading.RdLoadingPackageClaimMapper;
import com.ruiding.erp.dal.mysql.stock.RdStockLotMapper;
import com.ruiding.erp.dal.mysql.transfer.RdTransferOrderMapper;
import com.ruiding.erp.dal.mysql.transfer.RdTransferOrderPackageMapper;
import com.ruiding.erp.enums.RdPackageStatusEnum;
import com.ruiding.erp.enums.RdTransferOrderStatusEnum;
import com.ruiding.erp.service.masterdata.RdWarehouseLocationFreezeGuard;
import com.ruiding.erp.service.order.RdSaleOrderService;
import com.ruiding.erp.service.stock.RdStockTransactionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RdTransferOrderServiceImplTest {

    @Mock private RdTransferOrderMapper transferOrderMapper;
    @Mock private RdStockLotMapper stockLotMapper;
    @Mock private RdPackageUnitMapper packageUnitMapper;
    @Mock private RdLoadingPackageClaimMapper loadingClaimMapper;
    @Mock private RdTransferOrderPackageMapper transferOrderPackageMapper;
    @Mock private RdStockTransactionService stockTransactionService;
    @Mock private RdWarehouseLocationFreezeGuard locationFreezeGuard;
    @Mock private RdSaleOrderService saleOrderService;
    @InjectMocks private RdTransferOrderServiceImpl service;

    @Test
    void shouldMoveExactPackageToTargetLotOnConfirm() {
        RdTransferOrderDO order = transferOrder(RdTransferOrderStatusEnum.DRAFT.getStatus());
        RdStockLotDO source = sourceLot();
        RdPackageUnitDO unit = packageUnit().setStockLotId(50L).setCustomerName("Customer A");
        when(transferOrderMapper.selectByIdForUpdate(1L)).thenReturn(order);
        when(stockLotMapper.selectByIdForUpdate(50L)).thenReturn(source);
        when(saleOrderService.getSaleOrder(20L)).thenReturn(targetOrder());
        when(packageUnitMapper.selectListByStockLotIdForUpdate(
                50L, RdPackageStatusEnum.IN_STOCK.getStatus(), 1)).thenReturn(List.of(unit));
        doAnswer(invocation -> {
            ((RdStockLotDO) invocation.getArgument(0)).setId(60L);
            return 1;
        }).when(stockLotMapper).insert(any(RdStockLotDO.class));

        service.confirmTransferOrder(1L, 100L, "operator");

        verify(packageUnitMapper).updateById(org.mockito.ArgumentMatchers.argThat((RdPackageUnitDO value) ->
                Long.valueOf(60L).equals(value.getStockLotId()) && "Customer B".equals(value.getCustomerName())));
        verify(transferOrderPackageMapper).insert(org.mockito.ArgumentMatchers.argThat(
                (RdTransferOrderPackageDO value) -> Long.valueOf(1L).equals(value.getTransferOrderId())
                        && Long.valueOf(30L).equals(value.getPackageId())
                        && Long.valueOf(50L).equals(value.getFromStockLotId())
                        && Long.valueOf(60L).equals(value.getToStockLotId())));
    }

    @Test
    void shouldMoveRecordedPackageBackOnCancel() {
        RdTransferOrderDO order = transferOrder(RdTransferOrderStatusEnum.CONFIRMED.getStatus())
                .setToStockLotId(60L);
        RdStockLotDO source = sourceLot().setWarehouseQty(BigDecimal.ONE).setTransferOutQty(BigDecimal.ONE);
        RdStockLotDO target = sourceLot().setId(60L).setCustomerName("Customer B")
                .setWarehouseQty(BigDecimal.ONE).setTransferInQty(BigDecimal.ONE);
        RdPackageUnitDO unit = packageUnit().setStockLotId(60L).setCustomerName("Customer B");
        when(transferOrderMapper.selectByIdForUpdate(1L)).thenReturn(order);
        when(stockLotMapper.selectByIdForUpdate(50L)).thenReturn(source);
        when(stockLotMapper.selectByIdForUpdate(60L)).thenReturn(target);
        when(transferOrderPackageMapper.selectListByTransferOrderId(1L)).thenReturn(List.of(
                new RdTransferOrderPackageDO().setTransferOrderId(1L).setPackageId(30L)
                        .setPackageCode("PK-001").setFromStockLotId(50L).setToStockLotId(60L)));
        when(packageUnitMapper.selectByIdForUpdate(30L)).thenReturn(unit);

        service.cancelTransferOrder(1L, 100L, "operator");

        verify(packageUnitMapper).updateById(org.mockito.ArgumentMatchers.argThat((RdPackageUnitDO value) ->
                Long.valueOf(50L).equals(value.getStockLotId()) && "Customer A".equals(value.getCustomerName())));
    }

    @Test
    void shouldRejectFractionalPackageQuantity() {
        RdTransferOrderDO order = transferOrder(RdTransferOrderStatusEnum.DRAFT.getStatus())
                .setQuantity(new BigDecimal("1.5"));
        when(transferOrderMapper.selectByIdForUpdate(1L)).thenReturn(order);
        when(stockLotMapper.selectByIdForUpdate(50L)).thenReturn(sourceLot().setWarehouseQty(new BigDecimal("2")));
        when(saleOrderService.getSaleOrder(20L)).thenReturn(targetOrder());

        assertThrows(RuntimeException.class, () -> service.confirmTransferOrder(1L, 100L, "operator"));
    }

    private static RdTransferOrderDO transferOrder(String status) {
        return new RdTransferOrderDO().setId(1L).setTransferNo("DB-001")
                .setTransferDate(LocalDate.of(2026, 7, 12)).setToOrderId(20L)
                .setFromStockLotId(50L).setQuantity(BigDecimal.ONE)
                .setPackageWeight(new BigDecimal("1000")).setStatus(status);
    }

    private static RdStockLotDO sourceLot() {
        return new RdStockLotDO().setId(50L).setLocationId(10L).setLocationCode("A-01")
                .setCustomerName("Customer A").setProductModel("M-026")
                .setFurnaceNo("F-01").setFurnaceLayer("L1")
                .setWarehouseQty(new BigDecimal("2")).setLockedQty(BigDecimal.ZERO)
                .setTransferInQty(BigDecimal.ZERO).setTransferOutQty(BigDecimal.ZERO)
                .setPackageWeight(new BigDecimal("1000")).setStockWeight(new BigDecimal("2000"))
                .setStatus("AVAILABLE");
    }

    private static RdSaleOrderDO targetOrder() {
        return new RdSaleOrderDO().setId(20L).setCustomerName("Customer B").setProductModel("M-026");
    }

    private static RdPackageUnitDO packageUnit() {
        return new RdPackageUnitDO().setId(30L).setPackageCode("PK-001")
                .setLocationId(10L).setLocationCode("A-01")
                .setStatus(RdPackageStatusEnum.IN_STOCK.getStatus()).setVersion(1);
    }
}
