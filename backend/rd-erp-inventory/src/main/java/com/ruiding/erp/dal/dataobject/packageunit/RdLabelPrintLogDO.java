package com.ruiding.erp.dal.dataobject.packageunit;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("rd_label_print_log")
@Data
@EqualsAndHashCode(callSuper = true)
public class RdLabelPrintLogDO extends TenantBaseDO {
    @TableId private Long id;
    private Long packageId;
    private String templateCode;
    private Integer printSequence;
    private Boolean reprint;
    private String reprintReason;
    private Long printerId;
    private String printerName;
    private LocalDateTime printTime;
}
