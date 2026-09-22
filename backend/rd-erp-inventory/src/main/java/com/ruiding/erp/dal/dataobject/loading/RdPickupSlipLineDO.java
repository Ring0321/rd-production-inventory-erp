package com.ruiding.erp.dal.dataobject.loading;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@TableName("rd_pickup_slip_line")
@KeySequence("rd_pickup_slip_line_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class RdPickupSlipLineDO extends TenantBaseDO {
    @TableId private Long id;
    private Long slipId;
    private Integer lineNo;
    private Long saleOrderId;
    private String productName;
    private String productModel;
    private String furnaceNo;
    private String furnaceLayer;
    private Integer requestedPackageCount;
    private BigDecimal requestedWeight;
    private String remark;
}
