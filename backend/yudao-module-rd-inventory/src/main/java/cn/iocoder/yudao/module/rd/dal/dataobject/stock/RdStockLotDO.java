package cn.iocoder.yudao.module.rd.dal.dataobject.stock;

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
 * RD inventory lot ledger DO.
 */
@TableName("rd_stock_lot")
@KeySequence("rd_stock_lot_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RdStockLotDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long locationId;
    private String locationCode;
    private String customerName;
    private String productModel;
    private String furnaceNo;
    private String furnaceLayer;
    private BigDecimal openingQty;
    private BigDecimal transferInQty;
    private BigDecimal transferOutQty;
    private BigDecimal inboundQty;
    private BigDecimal outboundQty;
    private BigDecimal returnQty;
    private BigDecimal warehouseQty;
    private BigDecimal lockedQty;
    private BigDecimal packageWeight;
    private BigDecimal stockWeight;
    private LocalDate lastBizDate;
    private String status;
    private String remark;

}
