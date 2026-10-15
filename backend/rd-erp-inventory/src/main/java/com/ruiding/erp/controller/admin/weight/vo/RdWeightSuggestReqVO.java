package com.ruiding.erp.controller.admin.weight.vo;

import lombok.Data;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.math.BigDecimal;

@Data
public class RdWeightSuggestReqVO {
    @NotNull @Positive private Long pickupSlipId;
    @Positive private Long pickupSlipLineId;
    @NotNull @DecimalMin("0.001") @DecimalMax("1000000") @Digits(integer = 7, fraction = 3)
    private BigDecimal targetWeightKg;
    @NotNull @DecimalMin("0") @DecimalMax("1000000") @Digits(integer = 7, fraction = 3)
    private BigDecimal toleranceKg;
}
