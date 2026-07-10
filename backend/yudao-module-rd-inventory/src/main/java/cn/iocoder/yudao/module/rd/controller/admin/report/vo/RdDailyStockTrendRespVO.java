package cn.iocoder.yudao.module.rd.controller.admin.report.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RdDailyStockTrendRespVO {

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate bizDate;
    private BigDecimal productionQty;
    private BigDecimal inboundQty;
    private BigDecimal outboundQty;
    private BigDecimal transferInQty;
    private BigDecimal transferOutQty;
    private BigDecimal returnQty;
    private BigDecimal netChangeQty;

}
