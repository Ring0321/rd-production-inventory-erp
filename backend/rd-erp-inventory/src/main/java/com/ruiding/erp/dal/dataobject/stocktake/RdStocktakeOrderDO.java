package com.ruiding.erp.dal.dataobject.stocktake;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("rd_stocktake_order")
@KeySequence("rd_stocktake_order_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RdStocktakeOrderDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String stocktakeNo;
    private LocalDate stocktakeDate;
    private Long locationId;
    private String locationCode;
    private String status;
    private Integer version;
    private LocalDateTime snapshotTime;
    private BigDecimal bookPackageQty;
    private BigDecimal countedPackageQty;
    private BigDecimal shortageQty;
    private BigDecimal surplusQty;
    private BigDecimal bookWeight;
    private BigDecimal countedWeight;
    private Long businessCreatorId;
    private String businessCreatorName;
    private Long submitterId;
    private String submitterName;
    private LocalDateTime submitTime;
    private Long reviewerId;
    private String reviewerName;
    private LocalDateTime reviewTime;
    private Long adjusterId;
    private String adjusterName;
    private LocalDateTime adjustTime;
    private Long closerId;
    private String closerName;
    private LocalDateTime closeTime;
    private Long cancellerId;
    private String cancellerName;
    private LocalDateTime cancelTime;
    private String remark;
}
