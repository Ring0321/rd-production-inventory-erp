package com.ruiding.erp.service.outbound;

import com.ruiding.erp.enums.RdStockLotStatusEnum;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RdOutboundQuantityCalculatorTest {

    @Test
    void shouldConfirmAgainstAllocatedAndLockedQuantities() {
        RdOutboundQuantityCalculator.Result result = RdOutboundQuantityCalculator.confirm(
                decimal("100"), decimal("60"), decimal("10"),
                decimal("60"), decimal("20"), decimal("40"));
        assertDecimal("60", result.getWarehouseQty());
        assertDecimal("20", result.getLockedQty());
        assertDecimal("50", result.getStockOutboundQty());
        assertDecimal("60", result.getFulfillmentOutboundQty());
        assertDecimal("0", result.getRemainingAllocatedQty());
    }

    @Test
    void shouldRejectConfirmWhenAllocationIsInsufficient() {
        assertThrows(IllegalArgumentException.class, () -> RdOutboundQuantityCalculator.confirm(
                decimal("100"), decimal("60"), decimal("0"),
                decimal("60"), decimal("20"), decimal("41")));
    }

    @Test
    void shouldRejectConfirmWhenLockedStockIsInsufficient() {
        assertThrows(IllegalArgumentException.class, () -> RdOutboundQuantityCalculator.confirm(
                decimal("100"), decimal("39"), decimal("0"),
                decimal("60"), decimal("20"), decimal("40")));
    }

    @Test
    void shouldReverseConfirmedOutbound() {
        RdOutboundQuantityCalculator.Result result = RdOutboundQuantityCalculator.cancel(
                decimal("60"), decimal("20"), decimal("50"),
                decimal("60"), decimal("60"), decimal("20"));
        assertDecimal("80", result.getWarehouseQty());
        assertDecimal("40", result.getLockedQty());
        assertDecimal("30", result.getStockOutboundQty());
        assertDecimal("40", result.getFulfillmentOutboundQty());
        assertDecimal("20", result.getRemainingAllocatedQty());
    }

    @Test
    void shouldRejectReverseBeyondConfirmedQuantity() {
        assertThrows(IllegalArgumentException.class, () -> RdOutboundQuantityCalculator.cancel(
                decimal("60"), decimal("20"), decimal("10"),
                decimal("60"), decimal("10"), decimal("11")));
    }

    @Test
    void shouldRejectEmptyOrClosedStockStatus() {
        assertTrue(RdOutboundOrderServiceImpl.isStockUnavailable(RdStockLotStatusEnum.EMPTY.getStatus()));
        assertTrue(RdOutboundOrderServiceImpl.isStockUnavailable(RdStockLotStatusEnum.CLOSED.getStatus()));
        assertFalse(RdOutboundOrderServiceImpl.isStockUnavailable(RdStockLotStatusEnum.LOCKED.getStatus()));
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, decimal(expected).compareTo(actual));
    }

}
