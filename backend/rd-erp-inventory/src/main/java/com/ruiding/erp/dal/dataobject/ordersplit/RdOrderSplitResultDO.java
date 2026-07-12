package com.ruiding.erp.dal.dataobject.ordersplit;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("rd_order_split_result")
@KeySequence("rd_order_split_result_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class RdOrderSplitResultDO extends TenantBaseDO {
    @TableId
    private Long id;
    private String splitNo;
    private LocalDate splitDate;
    private Long saleOrderId;
    private String saleOrderNo;
    private Long ruleId;
    private String ruleNameSnapshot;
    private String customerNameSnapshot;
    private String productModelSnapshot;
    private BigDecimal furnaceLoadQty;
    private BigDecimal yieldRatePercent;
    private BigDecimal expectedOutputQty;
    private BigDecimal finalPlannedOutputQty;
    private BigDecimal varianceQty;
    @TableField(exist = false)
    private BigDecimal actualBoundOutputQty;
    private String status;
    private Integer version;
    private Long confirmerId;
    private String confirmerName;
    private LocalDateTime confirmTime;
    private Long cancellerId;
    private String cancellerName;
    private LocalDateTime cancelTime;
    private String cancelReason;
    private String remark;
}
