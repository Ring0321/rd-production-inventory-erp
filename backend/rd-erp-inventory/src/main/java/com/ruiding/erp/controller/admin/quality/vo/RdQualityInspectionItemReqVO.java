package com.ruiding.erp.controller.admin.quality.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import javax.validation.constraints.Digits;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RdQualityInspectionItemReqVO {
    @NotNull @Positive private Long samplePackageId;
    @NotBlank @Size(max = 64) private String itemCode;
    @NotBlank @Size(max = 128) private String itemName;
    @Digits(integer = 18, fraction = 6) private BigDecimal standardMin;
    @Digits(integer = 18, fraction = 6) private BigDecimal standardMax;
    @NotNull @Digits(integer = 18, fraction = 6) private BigDecimal measuredValue;
    @Size(max = 32) private String unit;
    @Size(max = 512) private String remark;
}
