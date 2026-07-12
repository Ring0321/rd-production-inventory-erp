package com.ruiding.erp.controller.admin.masterdata.vo;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
@Data
public class RdProductMasterSaveReqVO {
    private Long id;
    @NotBlank private String productCode;
    @NotBlank private String productName;
    @NotBlank private String customerName;
    @NotBlank private String productModel;
    private String category;
    private String unit;
    @NotNull private BigDecimal defaultPackageWeight;
    private String qualityStandard;
    private Boolean enabled;
    private String remark;
}
