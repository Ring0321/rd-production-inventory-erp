package com.ruiding.erp.controller.admin.stocktake.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class RdStocktakeActionReqVO {
    @NotNull private Long id;
    @NotNull private Integer version;
}
