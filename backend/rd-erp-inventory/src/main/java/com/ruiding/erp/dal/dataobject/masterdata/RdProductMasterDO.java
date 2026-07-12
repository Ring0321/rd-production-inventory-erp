package com.ruiding.erp.dal.dataobject.masterdata;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@TableName("rd_product_master")
@Data
@EqualsAndHashCode(callSuper = true)
public class RdProductMasterDO extends TenantBaseDO {
    @TableId private Long id;
    private String productCode;
    private String productName;
    private String customerName;
    private String productModel;
    private String category;
    private String unit;
    private BigDecimal defaultPackageWeight;
    private String qualityStandard;
    private Boolean enabled;
    private String remark;
}
