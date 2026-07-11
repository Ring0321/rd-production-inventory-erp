package com.ruiding.erp.dal.dataobject.stockmatrix;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RdStockMatrixDailyDO {
    private Long stockLotId;
    private LocalDate bizDate;
    private BigDecimal inboundQty;
    private BigDecimal outboundQty;
    private BigDecimal transferQty;
    private BigDecimal returnQty;
}
