package cn.iocoder.yudao.module.rd.controller.admin.transfer.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "管理后台 - RD 调拨单新增/修改 Request VO")
@Data
public class RdTransferOrderSaveReqVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "调拨单号", requiredMode = Schema.RequiredMode.REQUIRED, example = "TR-20260710-001")
    @NotEmpty(message = "调拨单号不能为空")
    private String transferNo;

    @Schema(description = "调拨日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "调拨日期不能为空")
    private LocalDate transferDate;

    @Schema(description = "调出订单编号", example = "2")
    private Long fromOrderId;

    @Schema(description = "调入订单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "调入订单编号不能为空")
    private Long toOrderId;

    @Schema(description = "调出库存批次编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    @NotNull(message = "调出库存批次编号不能为空")
    private Long fromStockLotId;

    @Schema(description = "调拨数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "20")
    @NotNull(message = "调拨数量不能为空")
    @DecimalMin(value = "0.000001", message = "调拨数量必须大于 0")
    private BigDecimal quantity;

    @Schema(description = "调拨原因")
    private String remark;

}
