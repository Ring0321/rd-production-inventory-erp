package com.ruiding.erp.controller.admin.excelimport.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - RD Excel 导入暂存行 Response VO")
@Data
public class RdExcelImportRowRespVO {

    private Long id;
    private Long taskId;
    private Integer rowNo;
    private String locationCode;
    private String customerName;
    private String productModel;
    private String furnaceNo;
    private String furnaceLayer;
    private BigDecimal sourceOpeningQty;
    private BigDecimal warehouseQty;
    private BigDecimal packageWeight;
    private BigDecimal stockWeight;
    private String status;
    private String errorMessage;
    private Long stockLotId;

}
