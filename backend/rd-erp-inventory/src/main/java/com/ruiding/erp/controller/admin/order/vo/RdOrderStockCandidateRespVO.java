package com.ruiding.erp.controller.admin.order.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "管理后台 - RD 订单可匹配库存 Response VO")
@Data
public class RdOrderStockCandidateRespVO {

    private Long id;
    private String locationCode;
    private String customerName;
    private String productModel;
    private String furnaceNo;
    private String furnaceLayer;
    private BigDecimal warehouseQty;
    private BigDecimal lockedQty;
    private BigDecimal availableQty;
    private BigDecimal packageWeight;
    private BigDecimal stockWeight;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate lastBizDate;
    private String status;
    private Boolean sameCustomer;

}
