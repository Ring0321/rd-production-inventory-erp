package com.ruiding.erp.dal.dataobject.putaway;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("rd_putaway_exception")
@KeySequence("rd_putaway_exception_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class RdPutawayExceptionDO extends TenantBaseDO {
    @TableId private Long id;
    private String exceptionNo;
    private Long taskId;
    private Long taskLineId;
    private Long packageId;
    private String packageCode;
    private String exceptionType;
    private String exceptionStatus;
    private String description;
    private String attachmentRefs;
    private Long reporterId;
    private String reporterName;
    private String reporterRole;
    private LocalDateTime reportedTime;
    private Long handlerId;
    private String handlerName;
    private LocalDateTime handleTime;
    private String handlingResult;
    private String resolutionAction;
    private Long previousLocationId;
    private String previousLocationCode;
    private Long resolvedLocationId;
    private String resolvedLocationCode;
    private Integer version;
    private String remark;
}
