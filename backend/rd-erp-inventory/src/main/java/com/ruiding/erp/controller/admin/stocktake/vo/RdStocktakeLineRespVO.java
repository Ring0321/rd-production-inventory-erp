package com.ruiding.erp.controller.admin.stocktake.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RdStocktakeLineRespVO {
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
    private String scannerName;
    private LocalDateTime scanTime;
    private Long stockTransactionId;
}
