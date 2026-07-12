package com.ruiding.erp.service.ordersplit;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
public class RdOrderSplitCalculation {
    private BigDecimal expectedOutputQty;
    private List<BigDecimal> baseQuantities;
    private List<BigDecimal> finalQuantities;
    private BigDecimal finalPlannedOutputQty;
    private BigDecimal varianceQty;
}
