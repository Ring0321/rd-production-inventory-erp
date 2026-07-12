package com.ruiding.erp.dal.dataobject.outbound;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@TableName("rd_outbound_order_line")
@Data
@EqualsAndHashCode(callSuper = true)
public class RdOutboundOrderLineDO extends TenantBaseDO {
    @TableId private Long id;
    private Long orderId;
    private Long packageId;
    private String packageCode;
    private Long stockLotId;
    private Long stockTransactionId;
    private Long locationId;
    private String locationCode;
    private String customerName;
    private String productModel;
    private String furnaceNo;
    private String furnaceLayer;
    private BigDecimal packageWeight;
    private String scanDevice;
    private Long scannerId;
    private String scannerName;
}
