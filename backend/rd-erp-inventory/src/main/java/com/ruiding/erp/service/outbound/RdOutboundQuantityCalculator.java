package com.ruiding.erp.service.outbound;

import java.math.BigDecimal;

/**
 * Applies outbound quantity changes without database concerns.
 */
public final class RdOutboundQuantityCalculator {

    private RdOutboundQuantityCalculator() {
    }

    public static Result confirm(BigDecimal warehouseQty, BigDecimal lockedQty,
                                 BigDecimal stockOutboundQty, BigDecimal allocatedQty,
                                 BigDecimal fulfillmentOutboundQty, BigDecimal quantity) {
        requirePositive(quantity);
        BigDecimal remainingAllocatedQty = allocatedQty.subtract(fulfillmentOutboundQty);
        if (remainingAllocatedQty.compareTo(quantity) < 0
                || lockedQty.compareTo(quantity) < 0
                || warehouseQty.compareTo(quantity) < 0) {
            throw new IllegalArgumentException("Outbound quantity exceeds allocated or locked stock");
        }
        return new Result(
                warehouseQty.subtract(quantity),
                lockedQty.subtract(quantity),
                stockOutboundQty.add(quantity),
                fulfillmentOutboundQty.add(quantity),
                remainingAllocatedQty.subtract(quantity));
    }

    public static Result cancel(BigDecimal warehouseQty, BigDecimal lockedQty,
                                BigDecimal stockOutboundQty, BigDecimal allocatedQty,
                                BigDecimal fulfillmentOutboundQty, BigDecimal quantity) {
        requirePositive(quantity);
        if (fulfillmentOutboundQty.compareTo(quantity) < 0
                || stockOutboundQty.compareTo(quantity) < 0) {
            throw new IllegalArgumentException("Cancellation exceeds confirmed outbound quantity");
        }
        BigDecimal afterFulfillmentOutboundQty = fulfillmentOutboundQty.subtract(quantity);
        return new Result(
                warehouseQty.add(quantity),
                lockedQty.add(quantity),
                stockOutboundQty.subtract(quantity),
                afterFulfillmentOutboundQty,
                allocatedQty.subtract(afterFulfillmentOutboundQty));
    }

    private static void requirePositive(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
    }

    public static final class Result {

        private final BigDecimal warehouseQty;
        private final BigDecimal lockedQty;
        private final BigDecimal stockOutboundQty;
        private final BigDecimal fulfillmentOutboundQty;
        private final BigDecimal remainingAllocatedQty;

        private Result(BigDecimal warehouseQty, BigDecimal lockedQty,
                       BigDecimal stockOutboundQty, BigDecimal fulfillmentOutboundQty,
                       BigDecimal remainingAllocatedQty) {
            this.warehouseQty = warehouseQty;
            this.lockedQty = lockedQty;
            this.stockOutboundQty = stockOutboundQty;
            this.fulfillmentOutboundQty = fulfillmentOutboundQty;
            this.remainingAllocatedQty = remainingAllocatedQty;
        }

        public BigDecimal getWarehouseQty() {
            return warehouseQty;
        }

        public BigDecimal getLockedQty() {
            return lockedQty;
        }

        public BigDecimal getStockOutboundQty() {
            return stockOutboundQty;
        }

        public BigDecimal getFulfillmentOutboundQty() {
            return fulfillmentOutboundQty;
        }

        public BigDecimal getRemainingAllocatedQty() {
            return remainingAllocatedQty;
        }
    }

}
