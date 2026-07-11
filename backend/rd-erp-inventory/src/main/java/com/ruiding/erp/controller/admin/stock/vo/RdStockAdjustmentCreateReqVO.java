package com.ruiding.erp.controller.admin.stock.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "管理后台 - RD 库存调整创建 Request VO")
@Data
public class RdStockAdjustmentCreateReqVO {

    @Schema(description = "库存批次编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "库存批次编号不能为空")
    private Long stockLotId;

    @Schema(description = "业务日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "业务日期不能为空")
    private LocalDate bizDate;

    @Schema(description = "调整类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "ADJUST_IN")
    @NotEmpty(message = "调整类型不能为空")
    private String adjustmentType;

    @Schema(description = "调整数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @NotNull(message = "调整数量不能为空")
    @DecimalMin(value = "0.000001", message = "调整数量必须大于 0")
    private BigDecimal quantity;

    @Schema(description = "调整原因", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "调整原因不能为空")
    private String remark;

}
