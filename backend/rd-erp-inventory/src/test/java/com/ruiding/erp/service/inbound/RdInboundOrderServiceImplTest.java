package com.ruiding.erp.service.inbound;

import com.ruiding.erp.dal.dataobject.inbound.RdInboundOrderDO;
import com.ruiding.erp.dal.dataobject.inbound.RdInboundOrderLineDO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockLotDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockTransactionDO;
import com.ruiding.erp.dal.mysql.inbound.RdInboundOrderLineMapper;
import com.ruiding.erp.dal.mysql.inbound.RdInboundOrderMapper;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.dal.mysql.stock.RdStockLotMapper;
import com.ruiding.erp.dal.mysql.stock.RdStockTransactionMapper;
import com.ruiding.erp.dal.mysql.production.RdProductionOutputMapper;
import com.ruiding.erp.dal.mysql.masterdata.RdWarehouseLocationMapper;
import com.ruiding.erp.dal.dataobject.production.RdProductionOutputDO;
import com.ruiding.erp.enums.RdProductionOutputStatusEnum;
import com.ruiding.erp.enums.RdInboundOrderStatusEnum;
import com.ruiding.erp.enums.RdPackageStatusEnum;
import com.ruiding.erp.service.masterdata.RdWarehouseLocationFreezeGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RdInboundOrderServiceImplTest {

    @Mock private RdInboundOrderMapper orderMapper;
    @Mock private RdInboundOrderLineMapper lineMapper;
    @Mock private RdPackageUnitMapper packageMapper;
    @Mock private RdStockLotMapper stockLotMapper;
    @Mock private RdStockTransactionMapper transactionMapper;
    @Mock private RdProductionOutputMapper productionMapper;
    @Mock private RdWarehouseLocationMapper locationMapper;
    @Mock private RdWarehouseLocationFreezeGuard locationFreezeGuard;
    @InjectMocks private RdInboundOrderServiceImpl service;

    @Test
    void shouldPostVerifiedOrderAtomicallyAndIdempotently() {
        RdInboundOrderDO order = new RdInboundOrderDO().setId(1L).setOrderNo("RK20260712001")
                .setBizDate(LocalDate.of(2026, 7, 12)).setStatus(RdInboundOrderStatusEnum.VERIFIED.getStatus());
        order.setCreator("100");
        RdInboundOrderLineDO line = new RdInboundOrderLineDO().setId(2L).setOrderId(1L).setPackageId(3L)
                .setLocationId(4L).setLocationCode("3-22");
        RdPackageUnitDO packageUnit = new RdPackageUnitDO().setId(3L).setPackageCode("20260712001")
                .setCustomerName("贝特瑞").setProductModel("026").setFurnaceNo("1-03#24")
                .setFurnaceLayer("下层【X】").setPackageWeight(new BigDecimal("1000"))
                .setStatus(RdPackageStatusEnum.INBOUND_PENDING.getStatus());
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(order);
        when(lineMapper.selectListByOrderId(1L)).thenReturn(List.of(line));
        when(packageMapper.selectByIdForUpdate(3L)).thenReturn(packageUnit);
        when(transactionMapper.selectByIdempotencyKey("INBOUND:1:3")).thenReturn(null);
        when(stockLotMapper.selectInboundLotForUpdate(any(), any(), any(), any(), any(), any()))
                .thenReturn(null);
        org.mockito.Mockito.doAnswer(invocation -> {
            ((RdStockLotDO) invocation.getArgument(0)).setId(9L);
            return 1;
        }).when(stockLotMapper).insert(any(RdStockLotDO.class));

        service.post(1L, 200L, "库房负责人");

        verify(transactionMapper, times(1)).insert((RdStockTransactionDO) any(RdStockTransactionDO.class));
        verify(packageMapper).updateById((RdPackageUnitDO) org.mockito.ArgumentMatchers.argThat((RdPackageUnitDO value) ->
                RdPackageStatusEnum.IN_STOCK.getStatus().equals(value.getStatus())));
    }

    @Test
    void shouldRejectPackageWithoutPassedQuality() {
        when(packageMapper.selectByIdForUpdate(3L)).thenReturn(new RdPackageUnitDO()
                .setId(3L).setStatus(RdPackageStatusEnum.QC_REJECTED.getStatus()));
        assertThrows(RuntimeException.class, () -> service.validatePackageForInbound(3L));
    }

    @Test
    void shouldRejectUnknownWarehouseLocationWhenCreatingInboundOrder() {
        when(productionMapper.selectByIdForUpdate(7L)).thenReturn(new RdProductionOutputDO().setId(7L)
                .setStatus(RdProductionOutputStatusEnum.CONFIRMED.getStatus()));
        when(packageMapper.selectByIdForUpdate(3L)).thenReturn(new RdPackageUnitDO().setId(3L)
                .setProductionOutputId(7L).setPackageWeight(new BigDecimal("1000"))
                .setStatus(RdPackageStatusEnum.QC_PASSED.getStatus()));
        RdInboundOrderLineDO line = new RdInboundOrderLineDO().setPackageId(3L)
                .setLocationId(99L).setLocationCode("UNKNOWN");

        assertThrows(RuntimeException.class, () -> service.create(
                LocalDate.of(2026, 7, 12), 7L, List.of(line), null));
    }

    @Test
    void shouldRejectSubmitterReviewingOwnInboundOrder() {
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(new RdInboundOrderDO().setId(1L)
                .setSubmitterId(200L).setStatus(RdInboundOrderStatusEnum.SUBMITTED.getStatus()));
        assertThrows(RuntimeException.class, () -> service.verify(1L, 200L, "self"));
    }

    @Test
    void shouldReversePostedOrderWithNegativeLedgerAndRestorePackageStatus() {
        RdInboundOrderDO order = new RdInboundOrderDO().setId(1L).setOrderNo("RK20260712001")
                .setBizDate(LocalDate.of(2026, 7, 12)).setStatus(RdInboundOrderStatusEnum.POSTED.getStatus());
        order.setCreator("100");
        RdInboundOrderLineDO line = new RdInboundOrderLineDO().setId(2L).setOrderId(1L)
                .setPackageId(3L).setStockLotId(9L).setStockTransactionId(11L);
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(order);
        when(lineMapper.selectListByOrderId(1L)).thenReturn(List.of(line));
        when(packageMapper.selectByIdForUpdate(3L)).thenReturn(new RdPackageUnitDO()
                .setId(3L).setPackageWeight(new BigDecimal("1000"))
                .setStatus(RdPackageStatusEnum.IN_STOCK.getStatus()));
        when(stockLotMapper.selectByIdForUpdate(9L)).thenReturn(new RdStockLotDO().setId(9L)
                .setWarehouseQty(BigDecimal.ONE).setInboundQty(BigDecimal.ONE)
                .setPackageWeight(new BigDecimal("1000")));
        when(transactionMapper.selectById(11L)).thenReturn(new RdStockTransactionDO()
                .setId(11L).setStockLotId(9L).setQuantity(BigDecimal.ONE));

        service.reverse(1L, 300L, "审计员", "入库信息错误");

        verify(transactionMapper).insert((RdStockTransactionDO) org.mockito.ArgumentMatchers.argThat((RdStockTransactionDO value) ->
                BigDecimal.ONE.negate().equals(value.getQuantity())
                        && Long.valueOf(11L).equals(value.getReversalTransactionId())));
        verify(packageMapper).updateById((RdPackageUnitDO) org.mockito.ArgumentMatchers.argThat((RdPackageUnitDO value) ->
                RdPackageStatusEnum.QC_PASSED.getStatus().equals(value.getStatus())));
        assertEquals(RdInboundOrderStatusEnum.REVERSED.getStatus(), order.getStatus());
    }
}
