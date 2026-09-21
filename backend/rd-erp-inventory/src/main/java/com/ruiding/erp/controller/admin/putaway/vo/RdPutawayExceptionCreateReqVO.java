package com.ruiding.erp.controller.admin.putaway.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
public class RdPutawayExceptionCreateReqVO {
    @NotNull(message = "上架任务不能为空")
    private Long id;

    @NotBlank(message = "异常类型不能为空")
    @Size(max = 64, message = "异常类型不能超过 64 个字符")
    private String exceptionType;

    @Size(max = 256, message = "包码内容不能超过 256 个字符")
    private String packageCode;

    @NotBlank(message = "异常说明不能为空")
    @Size(max = 1000, message = "异常说明不能超过 1000 个字符")
    private String description;

    @Size(max = 4000, message = "附件引用不能超过 4000 个字符")
    private String attachmentRefs;
}
