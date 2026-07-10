package cn.iocoder.yudao.module.rd.controller.admin.transfer.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - RD 调拨单 Response VO")
@Data
@ExcelIgnoreUnannotated
public class RdTransferOrderRespVO {

    @ExcelProperty("编号")
    private Long id;

    @ExcelProperty("调拨单号")
    private String transferNo;

    @ExcelProperty("调拨日期")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate transferDate;

    private Long fromOrderId;

    @ExcelProperty("调出订单")
    private String fromOrderNo;

    private Long toOrderId;

    @ExcelProperty("调入订单")
    private String toOrderNo;

    @ExcelProperty("调出客户")
    private String fromCustomerName;

    @ExcelProperty("调入客户")
    private String toCustomerName;

    @ExcelProperty("型号")
    private String productModel;

    private Long fromStockLotId;
    private Long toStockLotId;

    @ExcelProperty("调出货位")
    private String fromLocationCode;

    @ExcelProperty("调入货位")
    private String toLocationCode;

    @ExcelProperty("生产炉号")
    private String furnaceNo;

    @ExcelProperty("所在炉层")
    private String furnaceLayer;

    @ExcelProperty("调拨数量")
    private BigDecimal quantity;

    @ExcelProperty("单包重量")
    private BigDecimal packageWeight;

    @ExcelProperty("调拨重量")
    private BigDecimal weight;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("调拨原因")
    private String remark;

    @ExcelProperty("创建时间")
    private LocalDateTime createTime;

}
