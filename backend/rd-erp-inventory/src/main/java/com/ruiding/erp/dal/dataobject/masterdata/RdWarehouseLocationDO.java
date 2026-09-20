package com.ruiding.erp.dal.dataobject.masterdata;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@TableName("rd_location")
@Data
@EqualsAndHashCode(callSuper = true)
public class RdWarehouseLocationDO extends TenantBaseDO {
    @TableId private Long id;
    private String warehouseCode;
    private String warehouseName;
    private String areaCode;
    private String areaName;
    private String rowCode;
    private String columnCode;
    private String levelCode;
    private String locationCode;
    private String locationName;
    private BigDecimal capacityPackages;
    private BigDecimal capacityWeight;
    private BigDecimal occupiedPackages;
    private BigDecimal occupiedWeight;
    private BigDecimal reservedPackages;
    private BigDecimal reservedWeight;
    private String allowedCategory;
    private Boolean frozen;
    private Long stocktakeOrderId;
    private Boolean enabled;
    private String locationStatus;
    private Integer version;
    private String remark;
}
