package com.ruiding.erp.service.report;

import com.ruiding.erp.enums.RdStockTransactionTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Converts signed inventory ledger movements into positive report metrics.
 */
public final class RdDailyStockSummaryCalculator {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private RdDailyStockSummaryCalculator() {
    }

    public static Delta calculate(String transactionType, BigDecimal signedQuantity) {
        BigDecimal quantity = signedQuantity == null ? ZERO : signedQuantity;
        BigDecimal productionQty = ZERO;
        BigDecimal inboundQty = ZERO;
        BigDecimal outboundQty = ZERO;
        BigDecimal transferInQty = ZERO;
        BigDecimal transferOutQty = ZERO;
        BigDecimal returnQty = ZERO;
        if (RdStockTransactionTypeEnum.PRODUCTION_IN.getType().equals(transactionType)) {
            productionQty = quantity;
            inboundQty = quantity;
        } else if (RdStockTransactionTypeEnum.INBOUND.getType().equals(transactionType)) {
            inboundQty = quantity;
        } else if (RdStockTransactionTypeEnum.OUTBOUND.getType().equals(transactionType)) {
            outboundQty = quantity.negate();
        } else if (RdStockTransactionTypeEnum.TRANSFER_IN.getType().equals(transactionType)) {
            transferInQty = quantity;
        } else if (RdStockTransactionTypeEnum.TRANSFER_OUT.getType().equals(transactionType)) {
            transferOutQty = quantity.negate();
        } else if (RdStockTransactionTypeEnum.RETURN_IN.getType().equals(transactionType)) {
            returnQty = quantity;
        }
        return new Delta(productionQty, inboundQty, outboundQty, transferInQty,
                transferOutQty, returnQty, quantity);
    }

    @Getter
    @AllArgsConstructor
    public static class Delta {
        private final BigDecimal productionQty;
        private final BigDecimal inboundQty;
        private final BigDecimal outboundQty;
        private final BigDecimal transferInQty;
        private final BigDecimal transferOutQty;
        private final BigDecimal returnQty;
        private final BigDecimal netChangeQty;
    }

}
