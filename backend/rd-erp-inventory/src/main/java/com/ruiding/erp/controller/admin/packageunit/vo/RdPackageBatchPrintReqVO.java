package com.ruiding.erp.controller.admin.packageunit.vo;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.util.List;

@Data
public class RdPackageBatchPrintReqVO {
    @NotEmpty
    @Size(max = 500)
    private List<@NotNull @Positive Long> packageIds;
    @Size(max = 500)
    private String reprintReason;
}
