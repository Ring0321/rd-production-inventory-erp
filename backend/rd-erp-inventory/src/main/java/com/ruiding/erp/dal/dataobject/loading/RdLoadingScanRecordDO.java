package com.ruiding.erp.dal.dataobject.loading;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("rd_loading_scan_record")
@KeySequence("rd_loading_scan_record_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class RdLoadingScanRecordDO extends TenantBaseDO {
    @TableId private Long id;
    private Long taskId;
    private Long taskLineId;
    private Long packageId;
    private String packageCode;
    private Integer planVersion;
    private String idempotencyKey;
    private String requestDigest;
    private String scanResult;
    private Boolean success;
    private String errorCode;
    private String errorMessage;
    private String taskStatusBefore;
    private String taskStatusAfter;
    private String observedLocationCode;
    private String deviceCode;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime deviceTime;
    private LocalDateTime scanTime;
}
