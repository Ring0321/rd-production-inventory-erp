package com.ruiding.erp.dal.dataobject.ordersplit;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@TableName("rd_order_split_result_line")
@KeySequence("rd_order_split_result_line_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class RdOrderSplitResultLineDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long resultId;
    private Integer sequenceNo;
    private String furnaceLayer;
    private String allocationTypeSnapshot;
    private BigDecimal allocationValueSnapshot;
    private BigDecimal calculatedBaseQty;
    private BigDecimal manualAdjustmentQty;
    private BigDecimal finalPlannedQty;
    private BigDecimal actualBoundQty;
    private String adjustmentReason;
}
