package com.ruiding.erp.controller.admin.outbound.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@Accessors(chain = true)
public class RdOutboundScanReqVO {
    @NotNull private Long id;
    @NotBlank private String packageCode;
    private String deviceCode;
}
