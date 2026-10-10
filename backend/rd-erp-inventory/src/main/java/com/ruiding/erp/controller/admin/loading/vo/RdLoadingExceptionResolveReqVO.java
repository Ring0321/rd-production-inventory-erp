package com.ruiding.erp.controller.admin.loading.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
public class RdLoadingExceptionResolveReqVO {
    @NotNull(message = "装车任务不能为空")
    private Long taskId;

    @NotNull(message = "任务版本不能为空")
    private Integer version;

    @NotBlank(message = "处理动作不能为空")
    @Size(max = 64, message = "处理动作不能超过 64 个字符")
    private String resolutionAction;

    @NotBlank(message = "处理说明不能为空")
    @Size(max = 1000, message = "处理说明不能超过 1000 个字符")
    private String handlingResult;

    private Long resolvedLocationId;

    @Size(max = 64, message = "处理后库位不能超过 64 个字符")
    private String resolvedLocationCode;
}
