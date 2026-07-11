package com.ruiding.erp.dal.dataobject.masterdata;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("rd_customer_product")
@KeySequence("rd_customer_product_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class RdCustomerProductDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String customerName;
    private String productModel;
    private String sourceType;
    private LocalDateTime lastUsedTime;
    private Integer status;
}
