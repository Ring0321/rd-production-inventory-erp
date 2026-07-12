package com.ruiding.erp.dal.dataobject.ordersplit;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@TableName("rd_order_split_rule_line")
@KeySequence("rd_order_split_rule_line_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class RdOrderSplitRuleLineDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long ruleId;
    private Integer sequenceNo;
    private String furnaceLayer;
    private String allocationType;
    private BigDecimal allocationValue;
    private String remark;
}
