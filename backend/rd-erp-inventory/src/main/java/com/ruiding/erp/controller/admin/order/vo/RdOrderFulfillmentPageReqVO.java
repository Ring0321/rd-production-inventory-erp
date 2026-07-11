package com.ruiding.erp.controller.admin.order.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - RD 订单库存匹配分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RdOrderFulfillmentPageReqVO extends PageParam {

    @Schema(description = "销售订单编号", example = "1")
    private Long orderId;

    @Schema(description = "库存批次编号", example = "10")
    private Long stockLotId;

    @Schema(description = "状态", example = "ALLOCATED")
    private String status;

}
