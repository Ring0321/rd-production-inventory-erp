package com.ruiding.erp.dal.dataobject.order;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * RD sales order fulfillment extension DO.
 */
@TableName("rd_sale_order_extend")
@KeySequence("rd_sale_order_extend_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RdSaleOrderDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String orderNo;
    private LocalDate orderDate;
    private String customerName;
    private String productModel;
    private BigDecimal requiredQty;
    private BigDecimal manualOutputQty;
    private BigDecimal splitOutputQty;
    private BigDecimal actualOutputQty;
    private BigDecimal differenceQty;
    private BigDecimal allocatedQty;
    private BigDecimal transferInQty;
    private BigDecimal transferOutQty;
    private BigDecimal adjustedDifferenceQty;
    private BigDecimal fulfilledQty;
    private String status;
    private LocalDateTime closeTime;
    private String closeBy;
    private String remark;

}
