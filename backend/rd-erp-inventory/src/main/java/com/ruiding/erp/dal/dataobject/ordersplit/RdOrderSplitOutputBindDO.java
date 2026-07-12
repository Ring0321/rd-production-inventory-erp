package com.ruiding.erp.dal.dataobject.ordersplit;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("rd_order_split_output_bind")
@KeySequence("rd_order_split_output_bind_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class RdOrderSplitOutputBindDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long saleOrderId;
    private Long resultId;
    private Long resultLineId;
    private Long productionOutputId;
    private BigDecimal outputQtySnapshot;
    private String status;
    private Long binderId;
    private String binderName;
    private LocalDateTime bindTime;
    private Long unbinderId;
    private String unbinderName;
    private LocalDateTime unbindTime;
    private String unbindReason;
}
