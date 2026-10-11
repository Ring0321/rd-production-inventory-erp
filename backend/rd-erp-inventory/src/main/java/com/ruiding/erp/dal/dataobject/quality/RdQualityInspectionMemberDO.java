package com.ruiding.erp.dal.dataobject.quality;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@TableName("rd_quality_inspection_member")
@KeySequence("rd_quality_inspection_member_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class RdQualityInspectionMemberDO extends TenantBaseDO {
    @TableId private Long id;
    private Long inspectionId;
    private Long packageId;
    private String packageCode;
    private BigDecimal packageWeight;
    private Boolean sampled;
    // Package ID while claimed; NULL only after terminal review.
    private Long activeKey;
}
