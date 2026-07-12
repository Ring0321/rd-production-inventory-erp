package com.ruiding.erp.dal.dataobject.stocktake;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("rd_stocktake_order_line")
@KeySequence("rd_stocktake_order_line_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RdStocktakeOrderLineDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long orderId;
    private Long packageId;
    private String packageCode;
    private Long stockLotId;
    private String customerName;
    private String productModel;
    private String furnaceNo;
    private String furnaceLayer;
    private BigDecimal packageWeight;
    private BigDecimal bookQty;
    private BigDecimal countedQty;
    private BigDecimal differenceQty;
    private String result;
    private Long scannerId;
    private String scannerName;
    private LocalDateTime scanTime;
    private Long stockTransactionId;
}
