package com.ruiding.erp.service.stocktake;

import com.ruiding.erp.controller.admin.stocktake.vo.RdStocktakeScanReqVO;
import com.ruiding.erp.dal.dataobject.masterdata.RdWarehouseLocationDO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockLotDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockTransactionDO;
import com.ruiding.erp.dal.dataobject.stocktake.RdStocktakeOrderDO;
import com.ruiding.erp.dal.dataobject.stocktake.RdStocktakeOrderLineDO;
import com.ruiding.erp.dal.mysql.masterdata.RdWarehouseLocationMapper;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.dal.mysql.stock.RdStockLotMapper;
import com.ruiding.erp.dal.mysql.stock.RdStockTransactionMapper;
import com.ruiding.erp.dal.mysql.stocktake.RdStocktakeOrderLineMapper;
import com.ruiding.erp.dal.mysql.stocktake.RdStocktakeOrderMapper;
import com.ruiding.erp.enums.RdPackageStatusEnum;
import com.ruiding.erp.enums.RdStocktakeOrderStatusEnum;
import com.ruiding.erp.service.packageunit.RdScanEventService;
import com.ruiding.erp.service.stock.RdStockTransactionService;
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
class RdStocktakeOrderServiceImplTest {

    @Mock private RdStocktakeOrderMapper orderMapper;
    @Mock private RdStocktakeOrderLineMapper lineMapper;
    @Mock private RdPackageUnitMapper packageMapper;
    @Mock private RdStockLotMapper stockLotMapper;
    @Mock private RdStockTransactionMapper transactionMapper;
    @Mock private RdWarehouseLocationMapper locationMapper;
    @Mock private RdScanEventService scanEventService;
    @Mock private RdStockTransactionService stockTransactionService;
    @InjectMocks private RdStocktakeOrderServiceImpl service;

    @Test
    void shouldFreezeSnapshotAndBindLocationLock() {
        RdStocktakeOrderDO order = draftOrder();
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(order);
        when(locationMapper.selectByIdForUpdate(10L)).thenReturn(location().setFrozen(false).setStocktakeOrderId(null));
        when(packageMapper.selectListByLocationIdAndStatuses(10L,
                List.of(RdPackageStatusEnum.IN_STOCK.getStatus()))).thenReturn(List.of(inStockPackage()));

        service.freeze(1L, 0);

        verify(lineMapper).insert(org.mockito.ArgumentMatchers.argThat((RdStocktakeOrderLineDO line) ->
                Long.valueOf(1L).equals(line.getOrderId())
                        && BigDecimal.ONE.equals(line.getBookQty())
                        && BigDecimal.ZERO.equals(line.getCountedQty())));
        verify(locationMapper).updateById(org.mockito.ArgumentMatchers.argThat((RdWarehouseLocationDO value) ->
                Boolean.TRUE.equals(value.getFrozen()) && Long.valueOf(1L).equals(value.getStocktakeOrderId())));
        verify(orderMapper).updateById(org.mockito.ArgumentMatchers.argThat((RdStocktakeOrderDO value) ->
                RdStocktakeOrderStatusEnum.FROZEN.getStatus().equals(value.getStatus())
                        && assertBigDecimalEquals(BigDecimal.ONE, value.getBookPackageQty())
                        && assertBigDecimalEquals(new BigDecimal("1000"), value.getBookWeight())
                        && value.getSnapshotTime() != null));
        assertEquals(RdStocktakeOrderStatusEnum.FROZEN.getStatus(), order.getStatus());
    }

    @Test
    void shouldScanExpectedPackageAndMoveOrderToCounting() {
        RdStocktakeOrderDO order = frozenOrder();
        RdPackageUnitDO unit = inStockPackage();
        RdStocktakeOrderLineDO line = expectedLine().setCountedQty(BigDecimal.ZERO);
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(order);
        when(packageMapper.selectByPackageCodeForUpdate("PK-ST-001")).thenReturn(unit);
        when(lineMapper.selectByOrderIdAndPackageId(1L, 30L)).thenReturn(line);
        when(lineMapper.selectListByOrderId(1L)).thenReturn(List.of(line));

        RdStocktakeOrderLineDO result = service.scan(new RdStocktakeScanReqVO()
                .setId(1L).setPackageCode("PK-ST-001").setDeviceCode("D-01"), 200L, "scanner");

        assertEquals(BigDecimal.ONE, result.getCountedQty());
        verify(lineMapper, times(2)).updateById(org.mockito.ArgumentMatchers.argThat((RdStocktakeOrderLineDO value) ->
                BigDecimal.ONE.equals(value.getCountedQty()) && "scanner".equals(value.getScannerName())));
        verify(orderMapper).updateById(org.mockito.ArgumentMatchers.argThat((RdStocktakeOrderDO value) ->
                RdStocktakeOrderStatusEnum.COUNTING.getStatus().equals(value.getStatus())
                        && assertBigDecimalEquals(BigDecimal.ONE, value.getCountedPackageQty())
                        && assertBigDecimalEquals(new BigDecimal("1000"), value.getCountedWeight())));
        verify(scanEventService).record("STOCKTAKE", 1L, 30L, "PK-ST-001", "D-01", 200L, "scanner", true, null);
    }

    @Test
    void shouldRejectDuplicateOrCrossLocationScanAndRecordFailureEvent() {
        RdStocktakeOrderDO order = frozenOrder();
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(order);
        when(packageMapper.selectByPackageCodeForUpdate("PK-ST-001")).thenReturn(inStockPackage());
        when(lineMapper.selectByOrderIdAndPackageId(1L, 30L)).thenReturn(expectedLine().setCountedQty(BigDecimal.ONE));

        assertThrows(RuntimeException.class, () -> service.scan(new RdStocktakeScanReqVO()
                .setId(1L).setPackageCode("PK-ST-001").setDeviceCode("D-01"), 200L, "scanner"));

        verify(scanEventService).record("STOCKTAKE", 1L, 30L, "PK-ST-001", "D-01", 200L, "scanner",
                false, "该包码已扫描，请勿重复盘点");

        when(packageMapper.selectByPackageCodeForUpdate("PK-ST-001"))
                .thenReturn(inStockPackage().setLocationId(99L).setLocationCode("B-02"));

        assertThrows(RuntimeException.class, () -> service.scan(new RdStocktakeScanReqVO()
                .setId(1L).setPackageCode("PK-ST-001").setDeviceCode("D-01"), 200L, "scanner"));
    }

    @Test
    void shouldRejectSelfReview() {
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(submittedOrder().setSubmitterId(100L));

        assertThrows(RuntimeException.class, () -> service.approve(1L, 2, 100L, "self"));
    }

    @Test
    void shouldRejectBusinessCreatorReview() {
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(submittedOrder()
                .setSubmitterId(101L).setBusinessCreatorId(100L));

        assertThrows(RuntimeException.class, () -> service.approve(1L, 2, 100L, "creator"));
    }

    @Test
    void shouldRequireVersionForStateChange() {
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(draftOrder());

        assertThrows(RuntimeException.class, () -> service.freeze(1L, null));
        verify(locationMapper, never()).selectByIdForUpdate(any());
    }

    @Test
    void shouldRejectCancelAfterSubmission() {
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(submittedOrder());

        assertThrows(RuntimeException.class, () -> service.cancel(1L, 2, 500L, "operator"));
        verify(locationMapper, never()).selectByIdForUpdate(any());
    }

    @Test
    void shouldRejectShortageAdjustmentBelowLockedQty() {
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(approvedOrder());
        when(lineMapper.selectListByOrderId(1L)).thenReturn(List.of(expectedLine()
                .setCountedQty(BigDecimal.ZERO).setDifferenceQty(BigDecimal.ONE.negate()).setResult("SHORTAGE")));
        when(transactionMapper.selectByIdempotencyKey("STOCKTAKE:1:30:OUT")).thenReturn(null);
        when(packageMapper.selectByIdForUpdate(30L)).thenReturn(inStockPackage());
        when(stockLotMapper.selectByIdForUpdate(50L)).thenReturn(new RdStockLotDO().setId(50L)
                .setLocationId(10L).setLocationCode("A-01").setWarehouseQty(BigDecimal.ONE)
                .setLockedQty(BigDecimal.ONE).setPackageWeight(new BigDecimal("1000")).setStockWeight(new BigDecimal("1000"))
                .setStatus("LOCKED"));

        assertThrows(RuntimeException.class, () -> service.adjust(1L, 3, 300L, "adjuster"));
    }

    @Test
    void shouldAdjustShortageAndSurplusIdempotently() {
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(approvedOrder());
        when(lineMapper.selectListByOrderId(1L)).thenReturn(List.of(
                expectedLine().setDifferenceQty(BigDecimal.ONE.negate()).setResult("SHORTAGE"),
                surplusLine().setDifferenceQty(BigDecimal.ONE).setResult("SURPLUS")));
        when(transactionMapper.selectByIdempotencyKey("STOCKTAKE:1:30:OUT")).thenReturn(new RdStockTransactionDO().setId(91L));
        when(transactionMapper.selectByIdempotencyKey("STOCKTAKE:1:31:IN")).thenReturn(new RdStockTransactionDO().setId(92L));

        service.adjust(1L, 3, 300L, "adjuster");

        verify(stockLotMapper, never()).updateById(any(RdStockLotDO.class));
        verify(stockTransactionService, never()).createStockTransaction(any(RdStockTransactionDO.class));
        verify(orderMapper).updateById(org.mockito.ArgumentMatchers.argThat((RdStocktakeOrderDO value) ->
                RdStocktakeOrderStatusEnum.ADJUSTED.getStatus().equals(value.getStatus())
                        && "adjuster".equals(value.getAdjusterName())));
    }

    @Test
    void shouldPostShortageAndSurplusMovements() {
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(approvedOrder());
        when(lineMapper.selectListByOrderId(1L)).thenReturn(List.of(
                expectedLine().setCountedQty(BigDecimal.ZERO), surplusLine()));
        when(packageMapper.selectByIdForUpdate(30L)).thenReturn(inStockPackage());
        when(packageMapper.selectByIdForUpdate(31L)).thenReturn(inStockPackage().setId(31L)
                .setPackageCode("PK-ST-002").setStockLotId(51L)
                .setStatus(RdPackageStatusEnum.STOCKTAKE_MISSING.getStatus()));
        when(stockLotMapper.selectByIdForUpdate(50L)).thenReturn(new RdStockLotDO().setId(50L)
                .setWarehouseQty(new BigDecimal("2")).setLockedQty(BigDecimal.ZERO)
                .setStockWeight(new BigDecimal("2000")).setPackageWeight(new BigDecimal("1000")));
        when(stockLotMapper.selectByIdForUpdate(51L)).thenReturn(new RdStockLotDO().setId(51L)
                .setWarehouseQty(BigDecimal.ZERO).setLockedQty(BigDecimal.ZERO)
                .setStockWeight(BigDecimal.ZERO).setPackageWeight(new BigDecimal("1000")));
        when(stockTransactionService.createStockTransaction(any(RdStockTransactionDO.class)))
                .thenReturn(101L, 102L);

        service.adjust(1L, 3, 300L, "adjuster");

        verify(stockTransactionService).createStockTransaction(org.mockito.ArgumentMatchers.argThat(
                (RdStockTransactionDO value) -> BigDecimal.ONE.negate().equals(value.getQuantity())));
        verify(stockTransactionService).createStockTransaction(org.mockito.ArgumentMatchers.argThat(
                (RdStockTransactionDO value) -> BigDecimal.ONE.equals(value.getQuantity())));
        verify(packageMapper, times(2)).updateById(any(RdPackageUnitDO.class));
        verify(lineMapper).updateById(org.mockito.ArgumentMatchers.argThat(
                (RdStocktakeOrderLineDO value) -> Long.valueOf(101L).equals(value.getStockTransactionId())));
        verify(lineMapper).updateById(org.mockito.ArgumentMatchers.argThat(
                (RdStocktakeOrderLineDO value) -> Long.valueOf(102L).equals(value.getStockTransactionId())));
    }

    @Test
    void shouldCloseAndReleaseOnlyMatchingLocationLock() {
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(adjustedOrder());
        when(locationMapper.selectByIdForUpdate(10L)).thenReturn(location().setFrozen(true).setStocktakeOrderId(1L));

        service.close(1L, 4, 400L, "closer");

        verify(locationMapper).updateById(org.mockito.ArgumentMatchers.argThat((RdWarehouseLocationDO value) ->
                Boolean.FALSE.equals(value.getFrozen()) && value.getStocktakeOrderId() == null));
        verify(orderMapper).updateById(org.mockito.ArgumentMatchers.argThat((RdStocktakeOrderDO value) ->
                RdStocktakeOrderStatusEnum.CLOSED.getStatus().equals(value.getStatus())
                        && "closer".equals(value.getCloserName())));
    }

    @Test
    void shouldCancelWithoutReleasingOtherOrderLock() {
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(frozenOrder());
        when(locationMapper.selectByIdForUpdate(10L)).thenReturn(location().setFrozen(true).setStocktakeOrderId(99L));

        service.cancel(1L, 1, 500L, "canceller");

        verify(locationMapper, never()).updateById(org.mockito.ArgumentMatchers.argThat((RdWarehouseLocationDO value) ->
                Boolean.FALSE.equals(value.getFrozen())));
        verify(orderMapper).updateById(org.mockito.ArgumentMatchers.argThat((RdStocktakeOrderDO value) ->
                RdStocktakeOrderStatusEnum.CANCELLED.getStatus().equals(value.getStatus())
                        && "canceller".equals(value.getCancellerName())));
    }

    private static RdStocktakeOrderDO draftOrder() {
        return new RdStocktakeOrderDO().setId(1L).setStocktakeNo("PD20260712001")
                .setStocktakeDate(LocalDate.of(2026, 7, 12)).setLocationId(10L)
                .setLocationCode("A-01").setStatus(RdStocktakeOrderStatusEnum.DRAFT.getStatus()).setVersion(0);
    }

    private static RdStocktakeOrderDO frozenOrder() {
        return draftOrder().setStatus(RdStocktakeOrderStatusEnum.FROZEN.getStatus()).setVersion(1);
    }

    private static RdStocktakeOrderDO submittedOrder() {
        return draftOrder().setStatus(RdStocktakeOrderStatusEnum.SUBMITTED.getStatus()).setVersion(2);
    }

    private static RdStocktakeOrderDO approvedOrder() {
        return draftOrder().setStatus(RdStocktakeOrderStatusEnum.APPROVED.getStatus())
                .setVersion(3).setReviewerId(201L).setReviewerName("reviewer");
    }

    private static RdStocktakeOrderDO adjustedOrder() {
        return draftOrder().setStatus(RdStocktakeOrderStatusEnum.ADJUSTED.getStatus()).setVersion(4);
    }

    private static RdWarehouseLocationDO location() {
        return new RdWarehouseLocationDO().setId(10L).setLocationCode("A-01").setEnabled(true);
    }

    private static RdPackageUnitDO inStockPackage() {
        return new RdPackageUnitDO().setId(30L).setPackageCode("PK-ST-001").setStockLotId(50L)
                .setLocationId(10L).setLocationCode("A-01").setCustomerName("ACME")
                .setProductModel("M-026").setFurnaceNo("F-01").setFurnaceLayer("L1")
                .setPackageWeight(new BigDecimal("1000")).setStatus(RdPackageStatusEnum.IN_STOCK.getStatus());
    }

    private static RdStocktakeOrderLineDO expectedLine() {
        return new RdStocktakeOrderLineDO().setId(11L).setOrderId(1L).setPackageId(30L)
                .setPackageCode("PK-ST-001").setStockLotId(50L).setCustomerName("ACME")
                .setProductModel("M-026").setFurnaceNo("F-01").setFurnaceLayer("L1")
                .setPackageWeight(new BigDecimal("1000")).setBookQty(BigDecimal.ONE);
    }

    private static RdStocktakeOrderLineDO surplusLine() {
        return new RdStocktakeOrderLineDO().setId(12L).setOrderId(1L).setPackageId(31L)
                .setPackageCode("PK-ST-002").setStockLotId(51L).setCustomerName("ACME")
                .setProductModel("M-026").setFurnaceNo("F-01").setFurnaceLayer("L2")
                .setPackageWeight(new BigDecimal("800")).setBookQty(BigDecimal.ZERO).setCountedQty(BigDecimal.ONE);
    }

    private static boolean assertBigDecimalEquals(BigDecimal expected, BigDecimal actual) {
        return actual != null && expected.compareTo(actual) == 0;
    }
}
