package com.ruiding.erp.controller.admin.putaway.vo;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class RdPutawayBatchScanPreviewReqVO {
    private Long id;
    @NotBlank @Size(max = 256)
    private String packageCode;
}
