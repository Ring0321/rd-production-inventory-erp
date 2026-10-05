package com.ruiding.erp.controller.admin.inbound.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Accessors(chain = true)
@ExcelIgnoreUnannotated
public class RdInboundOrderLineExportRespVO {
    @ExcelProperty("入库单号") private String orderNo;
    @ExcelProperty("业务日期") private LocalDate bizDate;
    @ExcelProperty("吨袋包码") private String packageCode;
    @ExcelProperty("生产批次") private String batchCode;
    @ExcelProperty("产品型号") private String productModel;
    @ExcelProperty("库位") private String locationCode;
    @ExcelProperty("件数") private BigDecimal quantity;
    @ExcelProperty("重量(kg)") private BigDecimal weight;
    @ExcelProperty("单据状态") private String status;
    @ExcelProperty("备注") private String remark;
}
