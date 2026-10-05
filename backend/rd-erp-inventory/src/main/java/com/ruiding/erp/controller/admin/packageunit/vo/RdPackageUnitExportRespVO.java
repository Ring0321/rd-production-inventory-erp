package com.ruiding.erp.controller.admin.packageunit.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@ExcelIgnoreUnannotated
public class RdPackageUnitExportRespVO {
    @ExcelProperty("包码") private String packageCode;
    @ExcelProperty("二维码链接") private String qrCode;
    @ExcelProperty("生产记录ID") private Long productionOutputId;
    @ExcelProperty("生产日期") private LocalDate productionDate;
    @ExcelProperty("出炉日期") private LocalDate furnaceOutDate;
    @ExcelProperty("班次") private String shiftCode;
    @ExcelProperty("客户") private String customerName;
    @ExcelProperty("产品名称") private String productName;
    @ExcelProperty("型号") private String productModel;
    @ExcelProperty("炉号") private String furnaceNo;
    @ExcelProperty("炉层") private String furnaceLayer;
    @ExcelProperty("单包重量(kg)") private BigDecimal packageWeight;
    @ExcelProperty("货位") private String locationCode;
    @ExcelProperty("状态") private String status;
    @ExcelProperty("备注") private String remark;
}
