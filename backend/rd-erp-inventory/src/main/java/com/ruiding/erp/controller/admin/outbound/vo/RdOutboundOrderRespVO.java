package com.ruiding.erp.controller.admin.outbound.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - RD 出库单 Response VO")
@Data
@ExcelIgnoreUnannotated
public class RdOutboundOrderRespVO {

    @ExcelProperty("编号")
    private Long id;
    @ExcelProperty("出库单号")
    private String outboundNo;
    @ExcelProperty("出库日期")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate outboundDate;
    private Long orderId;
    @ExcelProperty("销售订单")
    private String orderNo;
    private Long fulfillmentId;
    private Long stockLotId;
    @ExcelProperty("客户")
    private String customerName;
    @ExcelProperty("型号")
    private String productModel;
    @ExcelProperty("货位")
    private String locationCode;
    @ExcelProperty("生产炉号")
    private String furnaceNo;
    @ExcelProperty("所在炉层")
    private String furnaceLayer;
    @ExcelProperty("出库数量")
    private BigDecimal quantity;
    @ExcelProperty("单包重量")
    private BigDecimal packageWeight;
    @ExcelProperty("出库重量")
    private BigDecimal weight;
    @ExcelProperty("状态")
    private String status;
    private Integer version;
    private String submitterName;
    private LocalDateTime submitTime;
    private String reviewerName;
    private LocalDateTime reviewTime;
    private String posterName;
    private LocalDateTime postTime;
    private String reverserName;
    private LocalDateTime reverseTime;
    private String reverseReason;
    @ExcelProperty("备注")
    private String remark;
    @ExcelProperty("创建时间")
    private LocalDateTime createTime;

}
