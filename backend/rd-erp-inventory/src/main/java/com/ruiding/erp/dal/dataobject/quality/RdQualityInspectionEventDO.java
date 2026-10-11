package com.ruiding.erp.dal.dataobject.quality;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@TableName("rd_quality_inspection_event")
@Data
@EqualsAndHashCode(callSuper = true)
public class RdQualityInspectionEventDO extends TenantBaseDO {
    @TableId private Long id;
    private Long inspectionId;
    private String action;
    private Long actorId;
    private String actorName;
    private String beforeStatus;
    private String afterStatus;
    private Integer coverageCount;
    private Integer sampleCount;
    private LocalDateTime eventTime;
    private String clientRequestId;
    private String detail;
}
