package com.ruiding.erp.controller.admin.packageunit.vo;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import lombok.Data;
@Data
public class RdPackagePrintReqVO {
    @NotNull @Positive private Long packageId;
    @Size(max = 500)
    private String reprintReason;
}
