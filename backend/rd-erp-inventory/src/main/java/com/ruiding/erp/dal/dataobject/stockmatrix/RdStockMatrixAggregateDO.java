package com.ruiding.erp.dal.dataobject.stockmatrix;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RdStockMatrixAggregateDO {
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
}
