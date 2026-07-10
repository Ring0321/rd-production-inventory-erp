package cn.iocoder.yudao.module.rd.controller.admin.stock.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - RD 库存台账 Response VO")
@Data
@ExcelIgnoreUnannotated
public class RdStockLotRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @ExcelProperty("编号")
    private Long id;

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

    @ExcelProperty("期初")
    private BigDecimal openingQty;

    @ExcelProperty("调入")
    private BigDecimal transferInQty;

    @ExcelProperty("调出")
    private BigDecimal transferOutQty;

    @ExcelProperty("入库")
    private BigDecimal inboundQty;

    @ExcelProperty("出库")
    private BigDecimal outboundQty;

    @ExcelProperty("退货")
    private BigDecimal returnQty;

    @ExcelProperty("库房数量")
    private BigDecimal warehouseQty;

    @ExcelProperty("锁定数量")
    private BigDecimal lockedQty;

    @ExcelProperty("可用数量")
    private BigDecimal availableQty;

    @ExcelProperty("单包重量")
    private BigDecimal packageWeight;

    @ExcelProperty("库存重量")
    private BigDecimal stockWeight;

    @ExcelProperty("最后业务日期")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate lastBizDate;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("备注")
    private String remark;

    @ExcelProperty("创建时间")
    private LocalDateTime createTime;

}
