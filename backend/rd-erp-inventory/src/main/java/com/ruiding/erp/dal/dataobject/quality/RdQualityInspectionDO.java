package com.ruiding.erp.dal.dataobject.quality;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("rd_quality_inspection")
@KeySequence("rd_quality_inspection_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class RdQualityInspectionDO extends TenantBaseDO {
    @TableId private Long id;
    private String inspectionNo;
    private Long packageId;
    private String status;
    private String conclusion;
    private String rejectReason;
    private Long submitterId;
    private String submitterName;
    private LocalDateTime submitTime;
    private Long reviewerId;
    private String reviewerName;
    private LocalDateTime reviewTime;
    private Integer version;
    private String remark;
}
