package com.ruiding.erp.controller.admin.loading.vo;

import lombok.Data;
import org.hibernate.validator.constraints.UniqueElements;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class RdLoadingBatchScanConfirmReqVO {
    @NotNull private Long taskId;
    @NotNull private Integer version;
    @NotBlank @Size(max = 128) private String representativePackageCode;
    @NotBlank @Size(max = 128) private String idempotencyKey;
    @NotEmpty @Size(min = 1, max = 500) @UniqueElements private List<@NotNull @Positive Long> taskLineIds;
    @Size(max = 64) private String deviceCode;
    private LocalDateTime deviceTime;
}
