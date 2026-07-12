package com.ruiding.erp.controller.admin.ordersplit.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class RdOrderSplitOutputBindRespVO {
    private Long id;
    private Long saleOrderId;
    private Long resultId;
    private Long resultLineId;
    private Long productionOutputId;
    private String furnaceNo;
    private String furnaceLayer;
    private LocalDate productionDate;
    private BigDecimal outputQtySnapshot;
    private String status;
    private String binderName;
    private LocalDateTime bindTime;
    private String unbinderName;
    private LocalDateTime unbindTime;
    private String unbindReason;
}
