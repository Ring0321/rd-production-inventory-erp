package com.ruiding.erp.controller.admin.ordersplit.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class RdOrderSplitActionReqVO {
    @NotNull private Long id;
    @NotNull private Integer version;
    private String reason;
}
