package com.ruiding.erp.controller.admin.masterdata.vo;
import javax.validation.constraints.NotBlank;
import lombok.Data;
import java.math.BigDecimal;
@Data
public class RdWarehouseLocationSaveReqVO {
    private Long id;
    @NotBlank private String warehouseCode;
    @NotBlank private String warehouseName;
    private String areaCode;
    private String areaName;
    @NotBlank private String locationCode;
    private String locationName;
    private BigDecimal capacityPackages;
    private String allowedCategory;
    private Boolean frozen;
    private Boolean enabled;
    private String remark;
}
