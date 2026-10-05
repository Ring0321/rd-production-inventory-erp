package com.ruiding.erp.controller.admin.loading.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Schema(description = "RD ERP - 装车任务确认出库过账 Request VO")
@Data
public class RdLoadingConfirmOutboundReqVO {

    @Schema(description = "装车任务 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1001")
    @NotNull(message = "装车任务不能为空")
    private Long taskId;

    @Schema(description = "任务版本号，用于防止多人同时确认", example = "2")
    private Integer version;

    @Schema(description = "确认说明")
    private String remark;
}
