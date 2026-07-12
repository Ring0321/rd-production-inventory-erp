package com.ruiding.erp.service.ordersplit;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class RdOrderSplitAllocation {
    private String allocationType;
    private BigDecimal allocationValue;
}
