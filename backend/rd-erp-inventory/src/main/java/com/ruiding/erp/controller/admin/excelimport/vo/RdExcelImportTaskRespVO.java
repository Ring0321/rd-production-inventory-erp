package com.ruiding.erp.controller.admin.excelimport.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - RD Excel 导入任务 Response VO")
@Data
public class RdExcelImportTaskRespVO {

    private Long id;
    private String fileName;
    private String sheetName;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate bizDate;
    private String status;
    private Integer totalRows;
    private Integer validRows;
    private Integer failedRows;
    private Integer skippedRows;
    private Integer importedRows;
    private LocalDateTime confirmTime;
    private String remark;
    private LocalDateTime createTime;

}
