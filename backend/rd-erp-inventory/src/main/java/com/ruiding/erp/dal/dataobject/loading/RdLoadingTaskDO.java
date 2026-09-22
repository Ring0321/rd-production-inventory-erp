package com.ruiding.erp.dal.dataobject.loading;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("rd_loading_task")
@KeySequence("rd_loading_task_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class RdLoadingTaskDO extends TenantBaseDO {
    @TableId private Long id;
    private String taskNo;
    private Long pickupSlipId;
    private String pickupSlipNo;
    private String status;
    private String customerName;
    private String vehiclePlate;
    private String transportDriverName;
    private String transportDriverPhone;
    private Long warehouseKeeperId;
    private String warehouseKeeperName;
    private Long forkliftDriverId;
    private String forkliftDriverName;
    private Long reviewerId;
    private String reviewerName;
    private Integer plannedPackageCount;
    private Integer scannedPackageCount;
    private BigDecimal plannedWeight;
    private BigDecimal scannedWeight;
    private Integer planVersion;
    private Integer version;
    private LocalDateTime assignedTime;
    private LocalDateTime startTime;
    private LocalDateTime scanCompleteTime;
    private LocalDateTime confirmTime;
    private LocalDateTime cancelTime;
    private String cancelReason;
    private String remark;
}
