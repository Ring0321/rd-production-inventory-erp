package com.ruiding.erp.controller.admin.loading.vo;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

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

    @Valid
    @Size(max = 100, message = "一次最多替换 100 包")
    private List<Replacement> replacements;

    @Data
    public static class Replacement {
        @NotNull(message = "原任务包不能为空")
        private Long taskLineId;

        @NotBlank(message = "替换包码不能为空")
        @Size(max = 128, message = "替换包码不能超过 128 个字符")
        private String newPackageCode;
    }
}
