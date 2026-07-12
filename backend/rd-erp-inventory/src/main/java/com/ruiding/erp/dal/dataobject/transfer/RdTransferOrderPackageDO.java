package com.ruiding.erp.dal.dataobject.transfer;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("rd_transfer_order_package")
@KeySequence("rd_transfer_order_package_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class RdTransferOrderPackageDO extends TenantBaseDO {
    @TableId private Long id;
    private Long transferOrderId;
    private Long packageId;
    private String packageCode;
    private Long fromStockLotId;
    private Long toStockLotId;
}
