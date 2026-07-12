package com.ruiding.erp.dal.dataobject.packageunit;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("rd_scan_event")
@Data
@EqualsAndHashCode(callSuper = true)
public class RdScanEventDO extends TenantBaseDO {
    @TableId private Long id;
    private String bizType;
    private Long bizId;
    private Long packageId;
    private String packageCode;
    private String deviceCode;
    private Long operatorId;
    private String operatorName;
    private Boolean success;
    private String errorMessage;
    private LocalDateTime scanTime;
}
