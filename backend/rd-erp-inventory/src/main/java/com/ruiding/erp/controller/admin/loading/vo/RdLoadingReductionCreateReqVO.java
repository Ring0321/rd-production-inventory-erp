package com.ruiding.erp.controller.admin.loading.vo;

import lombok.Data;
import javax.validation.constraints.*;
import java.util.List;

@Data
public class RdLoadingReductionCreateReqVO {
    @NotNull @Positive private Long taskId;
    @NotNull @Min(0) private Integer taskVersion;
    @NotEmpty @Size(max = 100) private List<@NotNull @Positive Long> taskLineIds;
    @NotBlank @Size(max = 1000) private String reason;
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{16,64}") private String idempotencyKey;
}
