package com.ruiding.erp.controller.admin.production.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class RdProductionOutputActionReqVO {
    @NotNull private Long id;
    @NotNull private Integer version;
}
