package com.ruiding.erp.controller.admin.loading.vo;

import lombok.Data;
import javax.validation.constraints.*;

@Data
public class RdLoadingReductionReviewReqVO {
    @NotNull @Positive private Long id;
    @NotNull @Min(0) private Integer version;
    @NotNull @Pattern(regexp = "APPROVED|REJECTED") private String decision;
    @NotBlank @Size(max = 1000) private String reviewRemark;
}
