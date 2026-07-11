package com.ruiding.erp.controller.admin.excelimport.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - RD Excel 导入错误 Response VO")
@Data
@ExcelIgnoreUnannotated
public class RdExcelImportErrorRespVO {

    private Long id;
    private Long taskId;
    @ExcelProperty("Excel 行号")
    private Integer rowNo;
    @ExcelProperty("字段")
    private String fieldName;
    @ExcelProperty("原始值")
    private String rawValue;
    @ExcelProperty("错误原因")
    private String errorMessage;

}
