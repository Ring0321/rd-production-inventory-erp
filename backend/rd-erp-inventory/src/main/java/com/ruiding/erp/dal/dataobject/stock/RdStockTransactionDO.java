package com.ruiding.erp.dal.dataobject.stock;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * RD inventory movement ledger DO.
 */
@TableName("rd_stock_transaction")
@KeySequence("rd_stock_transaction_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RdStockTransactionDO extends TenantBaseDO {

    @TableId
    private Long id;
    private LocalDate bizDate;
    private String transactionType;
    private Long stockLotId;
    private Long locationId;
    private String locationCode;
    private String customerName;
    private String productModel;
    private String furnaceNo;
    private String furnaceLayer;
    private BigDecimal quantity;
    private BigDecimal packageWeight;
    private BigDecimal weight;
    private BigDecimal beforeQty;
    private BigDecimal afterQty;
    private String sourceType;
    private String sourceNo;
    private String sourceLineNo;
    private Long relatedOrderId;
    private Long operatorId;
    private String operatorName;
    private String remark;

}
