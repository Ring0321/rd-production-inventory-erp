package com.ruiding.erp.controller.admin.production.vo;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDate;

@Data
public class RdProductionTaskSaveReqVO {
    private Long id;
    private Integer version;
    @NotNull private LocalDate productionDate;
    @NotBlank @Size(max = 128) private String customerName;
    @NotBlank @Size(max = 32) private String shiftCode;
    @Size(max = 1000) private String remark;
}
