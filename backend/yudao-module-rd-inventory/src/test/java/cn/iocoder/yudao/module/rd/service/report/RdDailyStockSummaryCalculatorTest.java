package cn.iocoder.yudao.module.rd.service.report;

import cn.iocoder.yudao.module.rd.enums.RdStockTransactionTypeEnum;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RdDailyStockSummaryCalculatorTest {

    @Test
    void shouldCountProductionInboundInBothProductionAndInboundMetrics() {
        RdDailyStockSummaryCalculator.Delta delta = RdDailyStockSummaryCalculator.calculate(
                RdStockTransactionTypeEnum.PRODUCTION_IN.getType(), decimal("20"));
        assertDecimal("20", delta.getProductionQty());
        assertDecimal("20", delta.getInboundQty());
        assertDecimal("20", delta.getNetChangeQty());
    }

    @Test
    void shouldDisplayOutboundAsPositiveAndReverseItWhenLedgerIsReversed() {
        RdDailyStockSummaryCalculator.Delta outbound = RdDailyStockSummaryCalculator.calculate(
                RdStockTransactionTypeEnum.OUTBOUND.getType(), decimal("-12"));
        RdDailyStockSummaryCalculator.Delta reversal = RdDailyStockSummaryCalculator.calculate(
                RdStockTransactionTypeEnum.OUTBOUND.getType(), decimal("5"));
        assertDecimal("12", outbound.getOutboundQty());
        assertDecimal("-5", reversal.getOutboundQty());
        assertDecimal("-12", outbound.getNetChangeQty());
        assertDecimal("5", reversal.getNetChangeQty());
    }

    @Test
    void shouldSeparateTransferDirections() {
        RdDailyStockSummaryCalculator.Delta transferIn = RdDailyStockSummaryCalculator.calculate(
                RdStockTransactionTypeEnum.TRANSFER_IN.getType(), decimal("8"));
        RdDailyStockSummaryCalculator.Delta transferOut = RdDailyStockSummaryCalculator.calculate(
                RdStockTransactionTypeEnum.TRANSFER_OUT.getType(), decimal("-8"));
        assertDecimal("8", transferIn.getTransferInQty());
        assertDecimal("8", transferOut.getTransferOutQty());
    }

    @Test
    void shouldKeepAdjustmentOnlyInNetChange() {
        RdDailyStockSummaryCalculator.Delta delta = RdDailyStockSummaryCalculator.calculate(
                RdStockTransactionTypeEnum.ADJUST_OUT.getType(), decimal("-3"));
        assertDecimal("0", delta.getInboundQty());
        assertDecimal("0", delta.getOutboundQty());
        assertDecimal("-3", delta.getNetChangeQty());
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, decimal(expected).compareTo(actual));
    }

}
