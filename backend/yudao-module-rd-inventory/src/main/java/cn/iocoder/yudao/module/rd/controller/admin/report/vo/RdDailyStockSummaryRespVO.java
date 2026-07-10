package cn.iocoder.yudao.module.rd.controller.admin.report.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@ExcelIgnoreUnannotated
public class RdDailyStockSummaryRespVO {

    private Long id;
    @ExcelProperty("日期")
    private LocalDate bizDate;
    private Long stockLotId;
    @ExcelProperty("货位")
    private String locationCode;
    @ExcelProperty("客户")
    private String customerName;
    @ExcelProperty("型号")
    private String productModel;
    @ExcelProperty("生产炉号")
    private String furnaceNo;
    @ExcelProperty("所在炉层")
    private String furnaceLayer;
    @ExcelProperty("生产量")
    private BigDecimal productionQty;
    @ExcelProperty("入库")
    private BigDecimal inboundQty;
    @ExcelProperty("出库")
    private BigDecimal outboundQty;
    @ExcelProperty("调入")
    private BigDecimal transferInQty;
    @ExcelProperty("调出")
    private BigDecimal transferOutQty;
    @ExcelProperty("退货")
    private BigDecimal returnQty;
    @ExcelProperty("净变化")
    private BigDecimal netChangeQty;
    @ExcelProperty("库房数量")
    private BigDecimal warehouseQty;
    @ExcelProperty("库存重量")
    private BigDecimal stockWeight;

}
