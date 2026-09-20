package com.ruiding.erp.dal.dataobject.putaway;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("rd_putaway_task_line")
@KeySequence("rd_putaway_task_line_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class RdPutawayTaskLineDO extends TenantBaseDO {
    @TableId private Long id;
    private Long taskId;
    private Integer lineNo;
    private Long packageId;
    private String packageCode;
    private String lineStatus;
    private String customerName;
    private String productModel;
    private String furnaceNo;
    private String furnaceLayer;
    private BigDecimal packageWeight;
    private Integer scanCount;
    private LocalDateTime firstScanTime;
    private LocalDateTime lastScanTime;
    private Long inboundOrderId;
    private Long inboundOrderLineId;
    private Long stockLotId;
    private Long stockTransactionId;
    private Integer version;
    private String remark;
}
