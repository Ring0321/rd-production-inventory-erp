package com.ruiding.erp.controller.admin.stockmatrix.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Accessors(chain = true)
public class RdStockMatrixDailyRespVO {
    private LocalDate bizDate;
    private BigDecimal inboundQty;
    private BigDecimal outboundQty;
    private BigDecimal transferQty;
    private BigDecimal returnQty;
}
