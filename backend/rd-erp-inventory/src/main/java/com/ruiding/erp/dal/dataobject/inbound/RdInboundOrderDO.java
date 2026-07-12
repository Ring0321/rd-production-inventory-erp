package com.ruiding.erp.dal.dataobject.inbound;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("rd_inbound_order")
@KeySequence("rd_inbound_order_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class RdInboundOrderDO extends TenantBaseDO {
    @TableId private Long id;
    private String orderNo;
    private LocalDate bizDate;
    private String sourceType;
    private Long productionOutputId;
    private BigDecimal totalPackages;
    private BigDecimal totalWeight;
    private String status;
    private Long submitterId;
    private String submitterName;
    private LocalDateTime submitTime;
    private Long reviewerId;
    private String reviewerName;
    private LocalDateTime reviewTime;
    private Long posterId;
    private String posterName;
    private LocalDateTime postTime;
    private Long reverserId;
    private String reverserName;
    private LocalDateTime reverseTime;
    private String reverseReason;
    private Integer version;
    private String remark;
}
