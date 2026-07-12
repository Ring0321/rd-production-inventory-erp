package com.ruiding.erp.controller.admin.production.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - RD 生产炉层产量 Response VO")
@Data
@ExcelIgnoreUnannotated
public class RdProductionOutputRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @ExcelProperty("编号")
    private Long id;

    private Integer version;

    @Schema(description = "产出日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("产出日期")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate productionDate;

    @Schema(description = "货位编号", example = "1001")
    private Long locationId;

    @Schema(description = "货位", example = "3-22")
    @ExcelProperty("货位")
    private String locationCode;

    @Schema(description = "客户", requiredMode = Schema.RequiredMode.REQUIRED, example = "贝特瑞产品一部")
    @ExcelProperty("客户")
    private String customerName;

    @Schema(description = "型号", requiredMode = Schema.RequiredMode.REQUIRED, example = "13031")
    @ExcelProperty("型号")
    private String productModel;

    @Schema(description = "生产炉号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1-13#27")
    @ExcelProperty("生产炉号")
    private String furnaceNo;

    @Schema(description = "所在炉层", requiredMode = Schema.RequiredMode.REQUIRED, example = "下层（X）")
    @ExcelProperty("所在炉层")
    private String furnaceLayer;

    @Schema(description = "实际产额", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @ExcelProperty("实际产额")
    private BigDecimal outputQty;

    @Schema(description = "单包重量", requiredMode = Schema.RequiredMode.REQUIRED, example = "1000")
    @ExcelProperty("单包重量")
    private BigDecimal packageWeight;

    @Schema(description = "产出重量", requiredMode = Schema.RequiredMode.REQUIRED, example = "1000")
    @ExcelProperty("产出重量")
    private BigDecimal outputWeight;

    @Schema(description = "已入库数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @ExcelProperty("已入库数量")
    private BigDecimal inboundQty;

    @Schema(description = "状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "DRAFT")
    @ExcelProperty("状态")
    private String status;

    @Schema(description = "来源表格")
    @ExcelProperty("来源表格")
    private String sourceSheet;

    @Schema(description = "备注")
    @ExcelProperty("备注")
    private String remark;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("创建时间")
    private LocalDateTime createTime;

}
