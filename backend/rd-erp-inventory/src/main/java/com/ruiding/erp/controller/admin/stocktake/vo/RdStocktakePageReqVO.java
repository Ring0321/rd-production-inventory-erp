package com.ruiding.erp.controller.admin.stocktake.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RdStocktakePageReqVO extends PageParam {
    private String stocktakeNo;
    private Long locationId;
    private String locationCode;
    private String status;
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate[] stocktakeDate;
}
