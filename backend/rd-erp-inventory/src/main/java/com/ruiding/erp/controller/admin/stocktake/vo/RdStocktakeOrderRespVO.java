package com.ruiding.erp.controller.admin.stocktake.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class RdStocktakeOrderRespVO {
    private Long id;
    private String stocktakeNo;
    private LocalDate stocktakeDate;
    private Long locationId;
    private String locationCode;
    private String status;
    private Integer version;
    private LocalDateTime snapshotTime;
    private BigDecimal bookPackageQty;
    private BigDecimal countedPackageQty;
    private BigDecimal shortageQty;
    private BigDecimal surplusQty;
    private BigDecimal bookWeight;
    private BigDecimal countedWeight;
    private String submitterName;
    private LocalDateTime submitTime;
    private String reviewerName;
    private LocalDateTime reviewTime;
    private String adjusterName;
    private LocalDateTime adjustTime;
    private String closerName;
    private LocalDateTime closeTime;
    private String remark;
}
