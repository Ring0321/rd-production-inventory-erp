package cn.iocoder.yudao.module.rd.controller.admin.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Schema(description = "管理后台 - RD 订单库存匹配 Request VO")
@Data
public class RdOrderFulfillmentAllocateReqVO {

    @Schema(description = "销售订单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "销售订单编号不能为空")
    private Long orderId;

    @Schema(description = "库存批次编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    @NotNull(message = "库存批次编号不能为空")
    private Long stockLotId;

    @Schema(description = "匹配数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "20")
    @NotNull(message = "匹配数量不能为空")
    @DecimalMin(value = "0.000001", message = "匹配数量必须大于 0")
    private BigDecimal quantity;

    @Schema(description = "备注")
    private String remark;

}
