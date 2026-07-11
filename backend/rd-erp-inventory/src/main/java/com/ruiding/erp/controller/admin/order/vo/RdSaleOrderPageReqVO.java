package com.ruiding.erp.controller.admin.order.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY;

@Schema(description = "管理后台 - RD 销售订单分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RdSaleOrderPageReqVO extends PageParam {

    @Schema(description = "订单号", example = "SO-20260710-001")
    private String orderNo;

    @Schema(description = "客户", example = "贝特瑞产品一部")
    private String customerName;

    @Schema(description = "型号", example = "13031")
    private String productModel;

    @Schema(description = "状态", example = "PARTIAL")
    private String status;

    @Schema(description = "订单日期范围")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate[] orderDate;

}
