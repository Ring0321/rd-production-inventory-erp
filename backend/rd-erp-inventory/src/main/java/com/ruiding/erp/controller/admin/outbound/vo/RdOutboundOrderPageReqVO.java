package com.ruiding.erp.controller.admin.outbound.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY;

@Schema(description = "管理后台 - RD 出库单分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RdOutboundOrderPageReqVO extends PageParam {

    @Schema(description = "出库单号", example = "OUT-20260710-001")
    private String outboundNo;

    @Schema(description = "销售订单编号", example = "1")
    private Long orderId;

    @Schema(description = "状态", example = "DRAFT")
    private String status;

    @Schema(description = "出库日期范围")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate[] outboundDate;

}
