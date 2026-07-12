package com.ruiding.erp.controller.admin.outbound.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class RdOutboundActionReqVO {
    @NotNull private Long id;
    private Integer version;
}
