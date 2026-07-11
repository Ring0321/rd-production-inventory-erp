package com.ruiding.erp.service.stockmatrix;

import com.ruiding.erp.enums.RdStockTransactionTypeEnum;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RdStockMatrixCalculatorTest {

    @Test
    void shouldClassifyInboundOutboundTransferReturnAndAdjustment() {
        assertMovement("8", "0", "0", "0", RdStockTransactionTypeEnum.PRODUCTION_IN, "8");
        assertMovement("3", "0", "0", "0", RdStockTransactionTypeEnum.OPENING, "3");
        assertMovement("2", "0", "0", "0", RdStockTransactionTypeEnum.ADJUST_IN, "2");
        assertMovement("0", "-5", "0", "0", RdStockTransactionTypeEnum.OUTBOUND, "-5");
        assertMovement("0", "-4", "0", "0", RdStockTransactionTypeEnum.ADJUST_OUT, "-4");
        assertMovement("0", "0", "6", "0", RdStockTransactionTypeEnum.TRANSFER_IN, "6");
        assertMovement("0", "0", "-6", "0", RdStockTransactionTypeEnum.TRANSFER_OUT, "-6");
        assertMovement("0", "0", "0", "7", RdStockTransactionTypeEnum.RETURN_IN, "7");
    }

    @Test
    void shouldCalculateClosingQuantityAndWeight() {
        BigDecimal closing = RdStockMatrixCalculator.closingQuantity(
                decimal("10"), decimal("3"), decimal("-2"), decimal("-1"), decimal("4"));

        assertDecimal("14", closing);
        assertDecimal("14000", RdStockMatrixCalculator.stockWeight(closing, decimal("1000")));
    }

    private static void assertMovement(String inbound, String outbound, String transfer, String returnQty,
                                       RdStockTransactionTypeEnum type, String quantity) {
        RdStockMatrixCalculator.Movement movement = RdStockMatrixCalculator.classify(type.getType(), decimal(quantity));
        assertDecimal(inbound, movement.getInboundQty());
        assertDecimal(outbound, movement.getOutboundQty());
        assertDecimal(transfer, movement.getTransferQty());
        assertDecimal(returnQty, movement.getReturnQty());
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, decimal(expected).compareTo(actual));
    }
}
