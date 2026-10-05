package com.ruiding.erp.controller.admin.outbound.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Accessors(chain = true)
@ExcelIgnoreUnannotated
public class RdOutboundOrderLineExportRespVO {
    @ExcelProperty("出库单号") private String outboundNo;
    @ExcelProperty("业务日期") private LocalDate outboundDate;
    @ExcelProperty("吨袋包码") private String packageCode;
    @ExcelProperty("生产批次") private String batchCode;
    @ExcelProperty("产品型号") private String productModel;
    @ExcelProperty("出库前库位") private String locationCode;
    @ExcelProperty("重量(kg)") private BigDecimal packageWeight;
    @ExcelProperty("扫描设备") private String scanDevice;
    @ExcelProperty("单据状态") private String status;
    @ExcelProperty("备注") private String remark;
}
