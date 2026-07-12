package com.ruiding.erp.controller.admin.packageunit.vo;
import javax.validation.constraints.NotNull;
import lombok.Data;
@Data
public class RdPackagePrintReqVO {
    @NotNull private Long packageId;
    private String reprintReason;
}
