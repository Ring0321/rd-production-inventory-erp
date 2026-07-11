package com.ruiding.erp.dal.dataobject.order;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * RD order-to-stock-lot fulfillment DO.
 */
@TableName("rd_order_fulfillment")
@KeySequence("rd_order_fulfillment_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RdOrderFulfillmentDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long orderId;
    private Long stockLotId;
    private BigDecimal matchedQty;
    private BigDecimal allocatedQty;
    private BigDecimal transferQty;
    private BigDecimal outboundQty;
    private String sourceType;
    private String status;
    private String remark;

}
