package com.ruiding.erp.controller.admin.ordersplit.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class RdOrderSplitUnbindReqVO {
    @NotNull private Long id;
    @NotNull private Integer version;
    @NotNull private Long productionOutputId;
    @NotBlank private String reason;
}
