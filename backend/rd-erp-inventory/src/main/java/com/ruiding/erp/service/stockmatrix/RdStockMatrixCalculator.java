package com.ruiding.erp.service.stockmatrix;

import com.ruiding.erp.enums.RdStockTransactionTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

public final class RdStockMatrixCalculator {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private RdStockMatrixCalculator() {
    }

    public static Movement classify(String transactionType, BigDecimal quantity) {
        BigDecimal value = quantity == null ? ZERO : quantity;
        BigDecimal inbound = ZERO;
        BigDecimal outbound = ZERO;
        BigDecimal transfer = ZERO;
        BigDecimal returnQty = ZERO;
        if (is(transactionType, RdStockTransactionTypeEnum.OPENING)
                || is(transactionType, RdStockTransactionTypeEnum.PRODUCTION_IN)
                || is(transactionType, RdStockTransactionTypeEnum.INBOUND)
                || is(transactionType, RdStockTransactionTypeEnum.ADJUST_IN)) {
            inbound = value;
        } else if (is(transactionType, RdStockTransactionTypeEnum.OUTBOUND)
                || is(transactionType, RdStockTransactionTypeEnum.ADJUST_OUT)) {
            outbound = value;
        } else if (is(transactionType, RdStockTransactionTypeEnum.TRANSFER_IN)
                || is(transactionType, RdStockTransactionTypeEnum.TRANSFER_OUT)) {
            transfer = value;
        } else if (is(transactionType, RdStockTransactionTypeEnum.RETURN_IN)) {
            returnQty = value;
        }
        return new Movement(inbound, outbound, transfer, returnQty);
    }

    public static BigDecimal closingQuantity(BigDecimal openingQty, BigDecimal inboundQty,
                                             BigDecimal outboundQty, BigDecimal transferQty,
                                             BigDecimal returnQty) {
        return value(openingQty).add(value(inboundQty)).add(value(outboundQty))
                .add(value(transferQty)).add(value(returnQty));
    }

    public static BigDecimal stockWeight(BigDecimal closingQty, BigDecimal packageWeight) {
        return value(closingQty).multiply(value(packageWeight));
    }

    private static boolean is(String type, RdStockTransactionTypeEnum expected) {
        return expected.getType().equals(type);
    }

    private static BigDecimal value(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    @Getter
    @AllArgsConstructor
    public static class Movement {
        private final BigDecimal inboundQty;
        private final BigDecimal outboundQty;
        private final BigDecimal transferQty;
        private final BigDecimal returnQty;
    }
}
