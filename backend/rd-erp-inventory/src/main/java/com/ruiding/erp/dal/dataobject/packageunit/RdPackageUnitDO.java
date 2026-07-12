package com.ruiding.erp.dal.dataobject.packageunit;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@TableName("rd_package_unit")
@KeySequence("rd_package_unit_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class RdPackageUnitDO extends TenantBaseDO {
    @TableId private Long id;
    private String packageCode;
    private String qrCode;
    private Long productionOutputId;
    private LocalDate productionDate;
    private LocalDate furnaceOutDate;
    private String shiftCode;
    private String customerName;
    private String productName;
    private String productModel;
    private String furnaceNo;
    private String furnaceLayer;
    private BigDecimal packageWeight;
    private Long locationId;
    private String locationCode;
    private Long stockLotId;
    private String status;
    private Integer version;
    private String remark;
}
