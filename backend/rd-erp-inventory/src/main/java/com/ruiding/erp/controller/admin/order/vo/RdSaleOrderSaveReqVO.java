package com.ruiding.erp.controller.admin.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "管理后台 - RD 销售订单新增/修改 Request VO")
@Data
public class RdSaleOrderSaveReqVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "订单号", requiredMode = Schema.RequiredMode.REQUIRED, example = "SO-20260710-001")
    @NotEmpty(message = "订单号不能为空")
    private String orderNo;

    @Schema(description = "订单日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "订单日期不能为空")
    private LocalDate orderDate;

    @Schema(description = "客户", requiredMode = Schema.RequiredMode.REQUIRED, example = "贝特瑞产品一部")
    @NotEmpty(message = "客户不能为空")
    private String customerName;

    @Schema(description = "型号", requiredMode = Schema.RequiredMode.REQUIRED, example = "13031")
    @NotEmpty(message = "型号不能为空")
    private String productModel;

    @Schema(description = "订单量", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @NotNull(message = "订单量不能为空")
    @DecimalMin(value = "0.000001", message = "订单量必须大于 0")
    private BigDecimal requiredQty;

    @Schema(description = "实际产额", example = "80")
    @DecimalMin(value = "0.000000", message = "实际产额不能小于 0")
    private BigDecimal actualOutputQty;

    @Schema(description = "Manual output quantity", example = "10")
    @DecimalMin(value = "0.000000", message = "Manual output quantity cannot be negative")
    private BigDecimal manualOutputQty;

    @Schema(description = "Split-bound output quantity (read-only)", accessMode = Schema.AccessMode.READ_ONLY)
    private BigDecimal splitOutputQty;

    @Schema(description = "备注")
    private String remark;

}
