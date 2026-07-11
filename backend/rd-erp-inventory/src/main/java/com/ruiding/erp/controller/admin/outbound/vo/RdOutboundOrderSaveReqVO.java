package com.ruiding.erp.controller.admin.outbound.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "管理后台 - RD 出库单新增/修改 Request VO")
@Data
public class RdOutboundOrderSaveReqVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "出库单号", requiredMode = Schema.RequiredMode.REQUIRED, example = "OUT-20260710-001")
    @NotEmpty(message = "出库单号不能为空")
    private String outboundNo;

    @Schema(description = "出库日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "出库日期不能为空")
    private LocalDate outboundDate;

    @Schema(description = "销售订单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "销售订单不能为空")
    private Long orderId;

    @Schema(description = "订单匹配记录编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    @NotNull(message = "订单匹配记录不能为空")
    private Long fulfillmentId;

    @Schema(description = "出库数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "20")
    @NotNull(message = "出库数量不能为空")
    @DecimalMin(value = "0.000001", message = "出库数量必须大于 0")
    private BigDecimal quantity;

    @Schema(description = "备注")
    private String remark;

}
