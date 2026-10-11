package com.ruiding.erp.dal.dataobject.production;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("rd_production_task")
@Data
@EqualsAndHashCode(callSuper = true)
public class RdProductionTaskDO extends TenantBaseDO {
    @TableId private Long id;
    private String taskNo;
    private LocalDate productionDate;
    private String customerName;
    private String shiftCode;
    private String status;
    private Integer version;
    private Long managerId;
    private String managerName;
    private LocalDateTime closedTime;
    private LocalDateTime cancelledTime;
    private String remark;
}
