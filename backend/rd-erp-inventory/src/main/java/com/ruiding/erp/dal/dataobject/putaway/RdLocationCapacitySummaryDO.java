package com.ruiding.erp.dal.dataobject.putaway;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RdLocationCapacitySummaryDO {
    private Long locationId;
    private BigDecimal packageCount;
    private BigDecimal totalWeight;
}
