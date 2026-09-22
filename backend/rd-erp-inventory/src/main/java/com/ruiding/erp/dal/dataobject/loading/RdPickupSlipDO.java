package com.ruiding.erp.dal.dataobject.loading;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("rd_pickup_slip")
@KeySequence("rd_pickup_slip_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class RdPickupSlipDO extends TenantBaseDO {
    @TableId private Long id;
    private String slipNo;
    private String status;
    private String customerName;
    private LocalDate pickupDate;
    private String vehiclePlate;
    private String transportDriverName;
    private String transportDriverPhone;
    private Integer requestedPackageCount;
    private BigDecimal requestedWeight;
    private String sourceAttachmentRef;
    private Long issuerId;
    private String issuerName;
    private LocalDateTime issuedTime;
    private Integer version;
    private String remark;
}
