package com.ruiding.erp.dal.dataobject.loading;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("rd_loading_package_claim")
@KeySequence("rd_loading_package_claim_seq")
@Data
public class RdLoadingPackageClaimDO {
    @TableId private Long id;
    private Long tenantId;
    private Long packageId;
    private Long taskId;
    private Long taskLineId;
    private LocalDateTime claimedTime;
}
