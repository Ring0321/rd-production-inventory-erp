package com.ruiding.erp.dal.dataobject.putaway;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("rd_putaway_task")
@KeySequence("rd_putaway_task_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class RdPutawayTaskDO extends TenantBaseDO {
    @TableId private Long id;
    private String taskNo;
    private String status;
    private Long plannedLocationId;
    private String plannedLocationCode;
    private Long finalLocationId;
    private String finalLocationCode;
    private Integer packageCount;
    private Integer scannedPackageCount;
    private Integer exceptionPackageCount;
    private BigDecimal totalWeight;
    private Long warehouseKeeperId;
    private String warehouseKeeperName;
    private Long forkliftDriverId;
    private String forkliftDriverName;
    private LocalDateTime assignedTime;
    private LocalDateTime startTime;
    private LocalDateTime scanCompleteTime;
    private LocalDateTime confirmTime;
    private LocalDateTime cancelTime;
    private String cancelReason;
    private Integer version;
    private String remark;
}
