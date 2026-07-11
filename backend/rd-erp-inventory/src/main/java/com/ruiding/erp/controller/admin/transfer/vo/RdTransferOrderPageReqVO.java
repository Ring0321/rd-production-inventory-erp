package com.ruiding.erp.controller.admin.transfer.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY;

@Schema(description = "管理后台 - RD 调拨单分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RdTransferOrderPageReqVO extends PageParam {

    @Schema(description = "调拨单号", example = "TR-20260710-001")
    private String transferNo;

    @Schema(description = "调出订单编号", example = "2")
    private Long fromOrderId;

    @Schema(description = "调入订单编号", example = "1")
    private Long toOrderId;

    @Schema(description = "状态", example = "DRAFT")
    private String status;

    @Schema(description = "调拨日期范围")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate[] transferDate;

}
