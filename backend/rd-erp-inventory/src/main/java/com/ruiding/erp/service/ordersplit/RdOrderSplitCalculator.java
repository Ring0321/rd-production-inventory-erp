package com.ruiding.erp.service.ordersplit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class RdOrderSplitCalculator {

    private static final int SCALE = 6;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public RdOrderSplitCalculation calculate(BigDecimal furnaceLoadQty, BigDecimal yieldRate,
                                              List<RdOrderSplitAllocation> lines) {
        requirePositive(furnaceLoadQty, "furnace load quantity");
        requirePositive(yieldRate, "yield rate");
        if (yieldRate.compareTo(HUNDRED) > 0 || lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("yield rate or allocation lines are invalid");
        }
        BigDecimal expected = furnaceLoadQty.multiply(yieldRate).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
        List<BigDecimal> base = new ArrayList<>();
        BigDecimal fixedTotal = BigDecimal.ZERO;
        BigDecimal ratioTotal = BigDecimal.ZERO;
        int lastRatioIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            RdOrderSplitAllocation line = lines.get(i);
            requirePositive(line.getAllocationValue(), "allocation value");
            base.add(BigDecimal.ZERO.setScale(SCALE));
            if ("FIXED_QTY".equals(line.getAllocationType())) {
                BigDecimal fixed = line.getAllocationValue().setScale(SCALE, RoundingMode.HALF_UP);
                base.set(i, fixed);
                fixedTotal = fixedTotal.add(fixed);
            } else if ("RATIO".equals(line.getAllocationType())) {
                ratioTotal = ratioTotal.add(line.getAllocationValue());
                lastRatioIndex = i;
            } else {
                throw new IllegalArgumentException("unsupported allocation type");
            }
        }
        if (fixedTotal.compareTo(expected) > 0) {
            throw new IllegalArgumentException("fixed quantity exceeds expected output");
        }
        BigDecimal remainder = expected.subtract(fixedTotal);
        BigDecimal allocatedRatio = BigDecimal.ZERO;
        if (lastRatioIndex >= 0) {
            for (int i = 0; i < lines.size(); i++) {
                if (!"RATIO".equals(lines.get(i).getAllocationType())) {
                    continue;
                }
                BigDecimal quantity = i == lastRatioIndex
                        ? remainder.subtract(allocatedRatio).setScale(SCALE, RoundingMode.HALF_UP)
                        : remainder.multiply(lines.get(i).getAllocationValue())
                        .divide(ratioTotal, SCALE, RoundingMode.HALF_UP);
                base.set(i, quantity);
                allocatedRatio = allocatedRatio.add(quantity);
            }
        }
        return build(expected, base, base);
    }

    public List<BigDecimal> applyAdjustments(List<BigDecimal> baseQuantities, List<BigDecimal> adjustments) {
        if (baseQuantities == null || adjustments == null || baseQuantities.size() != adjustments.size()) {
            throw new IllegalArgumentException("adjustments do not match lines");
        }
        List<BigDecimal> finals = new ArrayList<>();
        for (int i = 0; i < baseQuantities.size(); i++) {
            BigDecimal adjusted = baseQuantities.get(i).add(zeroIfNull(adjustments.get(i)))
                    .setScale(SCALE, RoundingMode.HALF_UP);
            if (adjusted.signum() < 0) {
                throw new IllegalArgumentException("final planned quantity cannot be negative");
            }
            finals.add(adjusted);
        }
        return finals;
    }

    public RdOrderSplitCalculation applyAdjustments(BigDecimal expected, List<BigDecimal> base,
                                                     List<BigDecimal> adjustments) {
        return build(expected, base, applyAdjustments(base, adjustments));
    }

    private RdOrderSplitCalculation build(BigDecimal expected, List<BigDecimal> base, List<BigDecimal> finals) {
        BigDecimal total = finals.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(SCALE, RoundingMode.HALF_UP);
        return new RdOrderSplitCalculation(expected, base, finals, total, total.subtract(expected));
    }

    private static void requirePositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
