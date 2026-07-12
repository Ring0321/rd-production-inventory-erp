package com.ruiding.erp.dal.dataobject.inbound;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@TableName("rd_inbound_order_line")
@KeySequence("rd_inbound_order_line_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class RdInboundOrderLineDO extends TenantBaseDO {
    @TableId private Long id;
    private Long orderId;
    private Long packageId;
    private Long locationId;
    private String locationCode;
    private BigDecimal quantity;
    private BigDecimal weight;
    private Long stockLotId;
    private Long stockTransactionId;
    private String remark;
}
