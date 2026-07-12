package com.ruiding.erp.controller.admin.quality.vo;
import javax.validation.constraints.NotNull;
import lombok.Data;
@Data
public class RdQualityReviewReqVO {
    @NotNull private Long id;
    private String reason;
}
