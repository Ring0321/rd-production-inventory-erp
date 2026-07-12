package com.ruiding.erp.controller.admin.outbound.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class RdOutboundReverseReqVO {
    @NotNull private Long id;
    @NotBlank private String reason;
    private Integer version;
}
