package cn.iocoder.yudao.module.rd.controller.admin.order.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - RD 销售订单 Response VO")
@Data
@ExcelIgnoreUnannotated
public class RdSaleOrderRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @ExcelProperty("编号")
    private Long id;

    @Schema(description = "订单号", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("订单号")
    private String orderNo;

    @Schema(description = "订单日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("订单日期")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate orderDate;

    @ExcelProperty("客户")
    private String customerName;

    @ExcelProperty("型号")
    private String productModel;

    @ExcelProperty("订单量")
    private BigDecimal requiredQty;

    @ExcelProperty("实际产额")
    private BigDecimal actualOutputQty;

    @ExcelProperty("调入量")
    private BigDecimal transferInQty;

    @ExcelProperty("调出量")
    private BigDecimal transferOutQty;

    @ExcelProperty("已分配量")
    private BigDecimal allocatedQty;

    @ExcelProperty("差额")
    private BigDecimal differenceQty;

    @ExcelProperty("调拨后差额")
    private BigDecimal adjustedDifferenceQty;

    @ExcelProperty("满足数量")
    private BigDecimal fulfilledQty;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("关闭时间")
    private LocalDateTime closeTime;

    @ExcelProperty("关闭人")
    private String closeBy;

    @ExcelProperty("备注")
    private String remark;

    @ExcelProperty("创建时间")
    private LocalDateTime createTime;

}
