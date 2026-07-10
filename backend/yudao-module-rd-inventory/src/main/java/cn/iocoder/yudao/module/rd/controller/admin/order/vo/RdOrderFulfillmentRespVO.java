package cn.iocoder.yudao.module.rd.controller.admin.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - RD 订单库存匹配 Response VO")
@Data
public class RdOrderFulfillmentRespVO {

    private Long id;
    private Long orderId;
    private Long stockLotId;
    private String locationCode;
    private String customerName;
    private String productModel;
    private String furnaceNo;
    private String furnaceLayer;
    private BigDecimal matchedQty;
    private BigDecimal allocatedQty;
    private BigDecimal transferQty;
    private BigDecimal outboundQty;
    private String sourceType;
    private String status;
    private String remark;
    private LocalDateTime createTime;

}
