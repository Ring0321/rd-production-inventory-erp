package com.ruiding.erp.dal.dataobject.putaway;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@TableName("rd_putaway_batch_scan_record")
@Data
@EqualsAndHashCode(callSuper = true)
public class RdPutawayBatchScanRecordDO extends TenantBaseDO {
    @TableId private Long id;
    private Long taskId;
    private String representativePackageCode;
    private String idempotencyKey;
    private String requestDigest;
    private Integer taskVersion;
    private String confirmedLineIds;
    private Integer confirmedCount;
    private Long operatorId;
    private String operatorName;
    private String deviceCode;
    private LocalDateTime scanTime;
}
