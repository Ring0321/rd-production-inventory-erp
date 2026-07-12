package com.ruiding.erp.service.outbound;

import com.ruiding.erp.controller.admin.outbound.vo.RdOutboundScanReqVO;
import com.ruiding.erp.dal.dataobject.order.RdOrderFulfillmentDO;
import com.ruiding.erp.dal.dataobject.order.RdSaleOrderDO;
import com.ruiding.erp.dal.dataobject.outbound.RdOutboundOrderDO;
import com.ruiding.erp.dal.dataobject.outbound.RdOutboundOrderLineDO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockLotDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockTransactionDO;
import com.ruiding.erp.dal.mysql.order.RdOrderFulfillmentMapper;
import com.ruiding.erp.dal.mysql.outbound.RdOutboundOrderLineMapper;
import com.ruiding.erp.dal.mysql.outbound.RdOutboundOrderMapper;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.dal.mysql.stock.RdStockLotMapper;
import com.ruiding.erp.dal.mysql.stock.RdStockTransactionMapper;
import com.ruiding.erp.enums.RdOutboundOrderStatusEnum;
import com.ruiding.erp.enums.RdPackageStatusEnum;
import com.ruiding.erp.service.order.RdSaleOrderService;
import com.ruiding.erp.service.packageunit.RdScanEventService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RdOutboundScanServiceImplTest {

    @Mock private RdOutboundOrderMapper orderMapper;
    @Mock private RdOutboundOrderLineMapper lineMapper;
    @Mock private RdOrderFulfillmentMapper fulfillmentMapper;
    @Mock private RdPackageUnitMapper packageMapper;
    @Mock private RdStockLotMapper stockLotMapper;
    @Mock private RdStockTransactionMapper transactionMapper;
    @Mock private RdSaleOrderService saleOrderService;
    @Mock private RdScanEventService scanEventService;
    @Mock private RdWarehouseLocationFreezeGuard locationFreezeGuard;
    @InjectMocks private RdOutboundOrderServiceImpl service;

    @Test
    void shouldScanOnlyMatchingInStockPackage() {
        RdOutboundOrderDO order = scanningOrder();
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(order);
        when(packageMapper.selectByPackageCodeForUpdate("PK20260712001")).thenReturn(packageUnit());
        when(lineMapper.selectByOrderIdAndPackageId(1L, 30L)).thenReturn(null);
        when(saleOrderService.getSaleOrder(10L)).thenReturn(saleOrder());
        when(fulfillmentMapper.selectById(20L)).thenReturn(fulfillment());

        RdOutboundOrderLineDO result = service.scanPackage(
                new RdOutboundScanReqVO().setId(1L).setPackageCode("PK20260712001").setDeviceCode("SCAN-01"),
                100L, "库房操作员");

        assertEquals(30L, result.getPackageId());
        verify(lineMapper).insert(any(RdOutboundOrderLineDO.class));
        verify(packageMapper).updateById((RdPackageUnitDO) org.mockito.ArgumentMatchers.argThat((RdPackageUnitDO value) ->
                RdPackageStatusEnum.OUTBOUND_PENDING.getStatus().equals(value.getStatus())));
    }

    @Test
    void shouldRejectDuplicateOrCrossOrderPackage() {
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(scanningOrder());
        when(packageMapper.selectByPackageCodeForUpdate("PK20260712001")).thenReturn(packageUnit());
        when(lineMapper.selectByOrderIdAndPackageId(1L, 30L)).thenReturn(new RdOutboundOrderLineDO().setId(99L));
        assertThrows(RuntimeException.class, () -> service.scanPackage(
                new RdOutboundScanReqVO().setId(1L).setPackageCode("PK20260712001"), 100L, "操作员"));

        RdPackageUnitDO wrongCustomer = packageUnit().setCustomerName("其他客户");
        when(lineMapper.selectByOrderIdAndPackageId(1L, 30L)).thenReturn(null);
        when(packageMapper.selectByPackageCodeForUpdate("PK20260712001")).thenReturn(wrongCustomer);
        when(saleOrderService.getSaleOrder(10L)).thenReturn(saleOrder());
        when(fulfillmentMapper.selectById(20L)).thenReturn(fulfillment());
        assertThrows(RuntimeException.class, () -> service.scanPackage(
                new RdOutboundScanReqVO().setId(1L).setPackageCode("PK20260712001"), 100L, "操作员"));
    }

    @Test
    void shouldPreventSubmitterApprovingOwnOrder() {
        RdOutboundOrderDO order = scanningOrder().setStatus(RdOutboundOrderStatusEnum.SUBMITTED.getStatus())
                .setSubmitterId(100L);
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(order);
        assertThrows(RuntimeException.class, () -> service.approveOutboundOrder(1L, null, 100L, "本人"));
    }

    @Test
    void shouldPostApprovedPackagesAtomicallyAndIdempotently() {
        RdOutboundOrderDO order = scanningOrder().setStatus(RdOutboundOrderStatusEnum.APPROVED.getStatus())
                .setQuantity(BigDecimal.ONE).setReviewerId(200L).setReviewerName("库房主管");
        RdOutboundOrderLineDO line = new RdOutboundOrderLineDO().setId(40L).setOrderId(1L)
                .setPackageId(30L).setPackageCode("PK20260712001").setStockLotId(50L)
                .setPackageWeight(new BigDecimal("1000"));
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(order);
        when(lineMapper.selectListByOrderId(1L)).thenReturn(List.of(line));
        when(transactionMapper.selectByIdempotencyKey("OUTBOUND:1:30")).thenReturn(null);
        when(packageMapper.selectByIdForUpdate(30L)).thenReturn(packageUnit()
                .setStatus(RdPackageStatusEnum.OUTBOUND_PENDING.getStatus()));
        when(stockLotMapper.selectByIdForUpdate(50L)).thenReturn(stockLot());
        when(fulfillmentMapper.selectByIdForUpdate(20L)).thenReturn(fulfillment());
        when(saleOrderService.lockSaleOrderForOutbound(10L, false)).thenReturn(saleOrder());
        when(fulfillmentMapper.sumOutboundQtyByOrderId(10L)).thenReturn(BigDecimal.ONE);

        service.postOutboundOrder(1L, null, 300L, "过账员");

        verify(transactionMapper).insert((RdStockTransactionDO) org.mockito.ArgumentMatchers.argThat((RdStockTransactionDO value) ->
                BigDecimal.ONE.negate().equals(value.getQuantity())
                        && Long.valueOf(30L).equals(value.getPackageId())));
        verify(packageMapper).updateById((RdPackageUnitDO) org.mockito.ArgumentMatchers.argThat((RdPackageUnitDO value) ->
                RdPackageStatusEnum.OUTBOUNDED.getStatus().equals(value.getStatus())));
        assertEquals(RdOutboundOrderStatusEnum.POSTED.getStatus(), order.getStatus());
    }

    @Test
    void shouldReversePostedPackagesWithPositiveLedger() {
        RdOutboundOrderDO order = scanningOrder().setStatus(RdOutboundOrderStatusEnum.POSTED.getStatus())
                .setQuantity(BigDecimal.ONE).setReviewerId(200L).setReviewerName("库房主管");
        RdOutboundOrderLineDO line = new RdOutboundOrderLineDO().setId(40L).setOrderId(1L)
                .setPackageId(30L).setPackageCode("PK20260712001").setStockLotId(50L)
                .setStockTransactionId(60L).setPackageWeight(new BigDecimal("1000"));
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(order);
        when(lineMapper.selectListByOrderId(1L)).thenReturn(List.of(line));
        when(transactionMapper.selectByIdempotencyKey("OUTBOUND-REVERSE:1:30")).thenReturn(null);
        when(transactionMapper.selectById(60L)).thenReturn(new RdStockTransactionDO().setId(60L));
        when(packageMapper.selectByIdForUpdate(30L)).thenReturn(packageUnit()
                .setStatus(RdPackageStatusEnum.OUTBOUNDED.getStatus()));
        when(stockLotMapper.selectByIdForUpdate(50L)).thenReturn(stockLot().setWarehouseQty(BigDecimal.ZERO)
                .setLockedQty(BigDecimal.ZERO).setOutboundQty(BigDecimal.ONE));
        when(fulfillmentMapper.selectByIdForUpdate(20L)).thenReturn(fulfillment().setOutboundQty(BigDecimal.ONE));
        when(saleOrderService.lockSaleOrderForOutbound(10L, true)).thenReturn(saleOrder());
        when(fulfillmentMapper.sumOutboundQtyByOrderId(10L)).thenReturn(BigDecimal.ZERO);

        service.reverseOutboundOrder(1L, null, 300L, "审计员", "客户退单");

        verify(transactionMapper).insert((RdStockTransactionDO) org.mockito.ArgumentMatchers.argThat((RdStockTransactionDO value) ->
                BigDecimal.ONE.equals(value.getQuantity()) && Long.valueOf(60L).equals(value.getReversalTransactionId())));
        verify(packageMapper).updateById((RdPackageUnitDO) org.mockito.ArgumentMatchers.argThat((RdPackageUnitDO value) ->
                RdPackageStatusEnum.IN_STOCK.getStatus().equals(value.getStatus())));
        assertEquals(RdOutboundOrderStatusEnum.REVERSED.getStatus(), order.getStatus());
    }

    private static RdOutboundOrderDO scanningOrder() {
        return new RdOutboundOrderDO().setId(1L).setOutboundNo("CK20260712001")
                .setOutboundDate(LocalDate.of(2026, 7, 12)).setOrderId(10L).setFulfillmentId(20L)
                .setStockLotId(50L).setStatus(RdOutboundOrderStatusEnum.SCANNING.getStatus());
    }

    private static RdSaleOrderDO saleOrder() {
        return new RdSaleOrderDO().setId(10L).setCustomerName("贝特瑞").setProductModel("026");
    }

    private static RdOrderFulfillmentDO fulfillment() {
        return new RdOrderFulfillmentDO().setId(20L).setOrderId(10L).setStockLotId(50L)
                .setAllocatedQty(BigDecimal.ONE).setOutboundQty(BigDecimal.ZERO).setStatus("ALLOCATED");
    }

    private static RdPackageUnitDO packageUnit() {
        return new RdPackageUnitDO().setId(30L).setPackageCode("PK20260712001")
                .setCustomerName("贝特瑞").setProductModel("026").setStockLotId(50L)
                .setLocationId(5L).setLocationCode("3-22").setFurnaceNo("1-03#24")
                .setFurnaceLayer("下层【X】").setPackageWeight(new BigDecimal("1000"))
                .setStatus(RdPackageStatusEnum.IN_STOCK.getStatus());
    }

    private static RdStockLotDO stockLot() {
        return new RdStockLotDO().setId(50L).setLocationId(5L).setLocationCode("3-22")
                .setCustomerName("贝特瑞").setProductModel("026").setFurnaceNo("1-03#24")
                .setFurnaceLayer("下层【X】").setWarehouseQty(BigDecimal.ONE).setLockedQty(BigDecimal.ONE)
                .setOutboundQty(BigDecimal.ZERO).setPackageWeight(new BigDecimal("1000"))
                .setStockWeight(new BigDecimal("1000")).setStatus("LOCKED");
    }
}
