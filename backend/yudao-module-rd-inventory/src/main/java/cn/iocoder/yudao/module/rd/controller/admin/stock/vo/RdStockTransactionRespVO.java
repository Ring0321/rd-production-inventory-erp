package cn.iocoder.yudao.module.rd.controller.admin.stock.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - RD 库存流水 Response VO")
@Data
@ExcelIgnoreUnannotated
public class RdStockTransactionRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @ExcelProperty("编号")
    private Long id;

    @Schema(description = "业务日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("业务日期")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate bizDate;

    @Schema(description = "流水类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "PRODUCTION_IN")
    @ExcelProperty("流水类型")
    private String transactionType;

    @Schema(description = "库存批次编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @ExcelProperty("库存批次编号")
    private Long stockLotId;

    @Schema(description = "货位编号", example = "1001")
    private Long locationId;

    @Schema(description = "货位", example = "3-22")
    @ExcelProperty("货位")
    private String locationCode;

    @Schema(description = "客户", example = "贝特瑞产品一部")
    @ExcelProperty("客户")
    private String customerName;

    @Schema(description = "型号", example = "13031")
    @ExcelProperty("型号")
    private String productModel;

    @Schema(description = "生产炉号", example = "1-13#27")
    @ExcelProperty("生产炉号")
    private String furnaceNo;

    @Schema(description = "所在炉层", example = "下层（X）")
    @ExcelProperty("所在炉层")
    private String furnaceLayer;

    @Schema(description = "变动数量", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("变动数量")
    private BigDecimal quantity;

    @Schema(description = "单包重量")
    @ExcelProperty("单包重量")
    private BigDecimal packageWeight;

    @Schema(description = "变动重量")
    @ExcelProperty("变动重量")
    private BigDecimal weight;

    @Schema(description = "变动前数量")
    @ExcelProperty("变动前数量")
    private BigDecimal beforeQty;

    @Schema(description = "变动后数量")
    @ExcelProperty("变动后数量")
    private BigDecimal afterQty;

    @Schema(description = "来源类型", example = "PRODUCTION_OUTPUT")
    @ExcelProperty("来源类型")
    private String sourceType;

    @Schema(description = "来源单号", example = "1024")
    @ExcelProperty("来源单号")
    private String sourceNo;

    @Schema(description = "来源行号", example = "下层（X）")
    @ExcelProperty("来源行号")
    private String sourceLineNo;

    @Schema(description = "关联订单编号", example = "2048")
    @ExcelProperty("关联订单编号")
    private Long relatedOrderId;

    @Schema(description = "操作人编号", example = "1")
    private Long operatorId;

    @Schema(description = "操作人", example = "管理员")
    @ExcelProperty("操作人")
    private String operatorName;

    @Schema(description = "备注")
    @ExcelProperty("备注")
    private String remark;

    @Schema(description = "创建时间")
    @ExcelProperty("创建时间")
    private LocalDateTime createTime;

}
