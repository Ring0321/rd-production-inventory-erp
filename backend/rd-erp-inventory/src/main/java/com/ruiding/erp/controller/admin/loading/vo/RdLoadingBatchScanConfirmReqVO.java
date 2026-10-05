package com.ruiding.erp.controller.admin.loading.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class RdLoadingBatchScanConfirmReqVO {
    @NotNull private Long taskId;
    @NotBlank private String representativePackageCode;
    @NotBlank private String idempotencyKey;
    @NotEmpty @Size(max = 500) private List<Long> taskLineIds;
    private String deviceCode;
    private LocalDateTime deviceTime;
}
