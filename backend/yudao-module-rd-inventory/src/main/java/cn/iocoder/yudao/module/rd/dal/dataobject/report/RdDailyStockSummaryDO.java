package cn.iocoder.yudao.module.rd.dal.dataobject.report;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;

@TableName("rd_daily_stock_summary")
@KeySequence("rd_daily_stock_summary_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RdDailyStockSummaryDO extends BaseDO {

    @TableId
    private Long id;
    private LocalDate bizDate;
    private Long stockLotId;
    private String locationCode;
    private String customerName;
    private String productModel;
    private String furnaceNo;
    private String furnaceLayer;
    private BigDecimal productionQty;
    private BigDecimal inboundQty;
    private BigDecimal outboundQty;
    private BigDecimal transferInQty;
    private BigDecimal transferOutQty;
    private BigDecimal returnQty;
    private BigDecimal netChangeQty;
    private BigDecimal warehouseQty;
    private BigDecimal stockWeight;

}
