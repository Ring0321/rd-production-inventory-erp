package com.ruiding.erp.dal.dataobject.loading;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("rd_loading_task_line")
@KeySequence("rd_loading_task_line_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class RdLoadingTaskLineDO extends TenantBaseDO {
    @TableId private Long id;
    private Long taskId;
    private Integer lineNo;
    private Long pickupSlipLineId;
    private Long packageId;
    private String packageCode;
    private Long saleOrderId;
    private Long fulfillmentId;
    private Long stockLotId;
    private Long plannedLocationId;
    private String plannedLocationCode;
    private String customerName;
    private String productModel;
    private String furnaceNo;
    private String furnaceLayer;
    private BigDecimal packageWeight;
    private String lineStatus;
    private Integer planVersion;
    private LocalDateTime scanTime;
    private Long outboundOrderId;
    private Long outboundOrderLineId;
    private Long stockTransactionId;
    private Integer version;
    private String remark;
}
