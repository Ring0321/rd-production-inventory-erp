package com.ruiding.erp.controller.admin.production.vo;

import lombok.Data;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

@Data
public class RdProductionTaskActionReqVO {
    @NotNull @Positive private Long id;
    @NotNull private Integer version;
}
