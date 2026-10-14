package com.ruiding.erp.dal.dataobject.loading;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("rd_loading_reduction")
public class RdLoadingReductionDO extends TenantBaseDO {
    @TableId private Long id;
    private Long taskId;
    private Long pickupSlipId;
    private String taskNo;
    private String pickupSlipNo;
    private String customerName;
    private String status;
    private Integer taskVersion;
    private Integer planVersion;
    private Integer slipVersion;
    private Integer version;
    private String idempotencyKey;
    @JsonIgnore private String requestDigest;
    @JsonIgnore private String planDigest;
    @JsonIgnore private String slipDigest;
    @JsonIgnore private String planLinesJson;
    private Integer originalPackageCount;
    private Integer reducedPackageCount;
    private Integer remainingPackageCount;
    @JsonFormat(shape = JsonFormat.Shape.STRING) private BigDecimal originalWeight;
    @JsonFormat(shape = JsonFormat.Shape.STRING) private BigDecimal reducedWeight;
    @JsonFormat(shape = JsonFormat.Shape.STRING) private BigDecimal remainingWeight;
    private String reason;
    private Long proposerId;
    private String proposerName;
    private LocalDateTime proposedTime;
    private Long reviewerId;
    private String reviewerName;
    private LocalDateTime reviewTime;
    private String reviewRemark;
}
