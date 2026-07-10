package cn.iocoder.yudao.module.rd.controller.admin.order.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - RD 订单可匹配库存分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RdOrderStockCandidatePageReqVO extends PageParam {

    @Schema(description = "销售订单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "销售订单编号不能为空")
    private Long orderId;

    @Schema(description = "是否包含跨客户库存", example = "false")
    private Boolean includeCrossCustomer = false;

}
