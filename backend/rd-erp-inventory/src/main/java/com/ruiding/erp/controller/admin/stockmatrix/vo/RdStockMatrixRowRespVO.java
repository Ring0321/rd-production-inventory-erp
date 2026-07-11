package com.ruiding.erp.controller.admin.stockmatrix.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

@Data
@Accessors(chain = true)
public class RdStockMatrixRowRespVO {
    private Long stockLotId;
    private String locationCode;
    private String customerName;
    private String productModel;
    private String furnaceNo;
    private String furnaceLayer;
    private BigDecimal openingQty;
    private BigDecimal transferQty;
    private BigDecimal inboundQty;
    private BigDecimal outboundQty;
    private BigDecimal returnQty;
    private BigDecimal warehouseQty;
    private BigDecimal packageWeight;
    private BigDecimal stockWeight;
    private List<RdStockMatrixDailyRespVO> dailyMovements;
}
