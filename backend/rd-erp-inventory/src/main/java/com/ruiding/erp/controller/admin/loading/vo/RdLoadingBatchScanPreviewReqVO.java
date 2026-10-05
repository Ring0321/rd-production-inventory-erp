package com.ruiding.erp.controller.admin.loading.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class RdLoadingBatchScanPreviewReqVO {
    private Long taskId;
    @NotBlank private String packageCode;
    private String deviceCode;
}
