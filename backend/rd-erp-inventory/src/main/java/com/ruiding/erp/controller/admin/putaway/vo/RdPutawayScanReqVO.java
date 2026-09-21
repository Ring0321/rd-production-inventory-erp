package com.ruiding.erp.controller.admin.putaway.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

@Data
public class RdPutawayScanReqVO {
    @NotNull(message = "上架任务不能为空")
    private Long id;

    @NotBlank(message = "包码不能为空")
    @Size(max = 256, message = "包码内容不能超过 256 个字符")
    private String packageCode;

    @NotBlank(message = "扫码幂等键不能为空")
    @Size(max = 128, message = "扫码幂等键不能超过 128 个字符")
    private String idempotencyKey;

    @Size(max = 64, message = "设备编号不能超过 64 个字符")
    private String deviceCode;

    private LocalDateTime deviceTime;
}
