package com.ruiding.erp.dal.dataobject.packageunit;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("rd_package_task_event")
@Data
@EqualsAndHashCode(callSuper = true)
public class RdPackageTaskEventDO extends TenantBaseDO {
    @TableId private Long id;
    private Long packageId;
    private String packageCode;
    private String stage;
    private String eventCode;
    private String sourceType;
    private Long sourceId;
    private String sourceNo;
    private Integer sourceVersion;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime eventTime;
    private String locationCode;
    private String previousLocationCode;
    private String relatedPackageCode;
    private String detail;
}
