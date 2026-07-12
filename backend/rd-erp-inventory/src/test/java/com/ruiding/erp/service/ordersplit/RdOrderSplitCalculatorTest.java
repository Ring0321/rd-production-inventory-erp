package com.ruiding.erp.service.ordersplit;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RdOrderSplitCalculatorTest {

    private final RdOrderSplitCalculator calculator = new RdOrderSplitCalculator();

    @Test
    void shouldAllocateFixedLines() {
        RdOrderSplitCalculation result = calculator.calculate(new BigDecimal("100"), new BigDecimal("90"),
                List.of(line("FIXED_QTY", "30"), line("FIXED_QTY", "40")));
        assertEquals(new BigDecimal("90.000000"), result.getExpectedOutputQty());
        assertEquals(List.of(new BigDecimal("30.000000"), new BigDecimal("40.000000")), result.getBaseQuantities());
        assertEquals(new BigDecimal("-20.000000"), result.getVarianceQty());
    }

    @Test
    void shouldAllocateRatioLines() {
        RdOrderSplitCalculation result = calculator.calculate(new BigDecimal("10"), new BigDecimal("80"),
                List.of(line("RATIO", "1"), line("RATIO", "3")));
        assertEquals(List.of(new BigDecimal("2.000000"), new BigDecimal("6.000000")), result.getBaseQuantities());
    }

    @Test
    void shouldAllocateFixedBeforeRatio() {
        RdOrderSplitCalculation result = calculator.calculate(new BigDecimal("100"), new BigDecimal("90"),
                List.of(line("FIXED_QTY", "30"), line("RATIO", "1"), line("RATIO", "2")));
        assertEquals(List.of(new BigDecimal("30.000000"), new BigDecimal("20.000000"),
                new BigDecimal("40.000000")), result.getBaseQuantities());
    }

    @Test
    void shouldPutRoundingRemainderOnLastRatioLine() {
        RdOrderSplitCalculation result = calculator.calculate(BigDecimal.ONE, new BigDecimal("100"),
                List.of(line("RATIO", "1"), line("RATIO", "1"), line("RATIO", "1")));
        assertEquals(List.of(new BigDecimal("0.333333"), new BigDecimal("0.333333"),
                new BigDecimal("0.333334")), result.getBaseQuantities());
    }

    @Test
    void shouldRejectFixedOverflow() {
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(new BigDecimal("10"),
                new BigDecimal("80"), List.of(line("FIXED_QTY", "9"))));
    }

    @Test
    void shouldRejectNegativeAdjustedFinal() {
        assertThrows(IllegalArgumentException.class, () -> calculator.applyAdjustments(
                List.of(new BigDecimal("3.000000")), List.of(new BigDecimal("-4"))));
    }

    private static RdOrderSplitAllocation line(String type, String value) {
        return new RdOrderSplitAllocation(type, new BigDecimal(value));
    }
}
