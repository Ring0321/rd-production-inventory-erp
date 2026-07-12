package com.ruiding.erp.controller.admin.packageunit.vo;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Data;
@Data
public class RdPackageGenerateReqVO {
    @NotNull private Long productionOutputId;
    @NotNull @Min(1) private Integer packageCount;
}
