package com.ruiding.erp.dal.dataobject.quality;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@TableName("rd_quality_inspection_item")
@KeySequence("rd_quality_inspection_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class RdQualityInspectionItemDO extends TenantBaseDO {
    @TableId private Long id;
    private Long inspectionId;
    private String itemCode;
    private String itemName;
    private BigDecimal standardMin;
    private BigDecimal standardMax;
    private BigDecimal measuredValue;
    private String unit;
    private Boolean qualified;
    private String remark;
}
