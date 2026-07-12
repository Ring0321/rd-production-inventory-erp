package com.ruiding.erp.controller.admin.inbound.vo;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;
@Data
public class RdInboundReverseReqVO {
    @NotNull private Long id;
    @NotBlank private String reason;
}
