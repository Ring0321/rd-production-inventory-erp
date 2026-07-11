package com.ruiding.erp.controller.admin.stockmatrix.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY;

@Schema(description = "RD stock matrix page request")
@Data
@EqualsAndHashCode(callSuper = true)
public class RdStockMatrixPageReqVO extends PageParam {

    @NotNull(message = "startDate is required")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate startDate;

    @NotNull(message = "endDate is required")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate endDate;

    private String locationCode;
    private String customerName;
    private String productModel;
    private String furnaceNo;

    @AssertTrue(message = "date range must be between 1 and 31 days")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null
                || (!endDate.isBefore(startDate) && ChronoUnit.DAYS.between(startDate, endDate) <= 30);
    }
}
