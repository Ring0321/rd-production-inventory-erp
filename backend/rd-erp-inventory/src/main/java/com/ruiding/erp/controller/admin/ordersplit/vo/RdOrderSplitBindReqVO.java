package com.ruiding.erp.controller.admin.ordersplit.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class RdOrderSplitBindReqVO {
    @NotNull private Long id;
    @NotNull private Integer version;
    @NotNull private Long resultLineId;
    @NotNull private Long productionOutputId;
}
