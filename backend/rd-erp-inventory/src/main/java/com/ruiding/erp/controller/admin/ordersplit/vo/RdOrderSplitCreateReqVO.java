package com.ruiding.erp.controller.admin.ordersplit.vo;

import lombok.Data;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RdOrderSplitCreateReqVO {
    @NotNull private LocalDate splitDate;
    @NotNull private Long saleOrderId;
    @NotNull private Long ruleId;
    @NotNull @DecimalMin("0.000001") private BigDecimal furnaceLoadQty;
    @NotNull @DecimalMin("0.000001") @DecimalMax("100") private BigDecimal yieldRatePercent;
    private String remark;
}
