package com.ruiding.erp.controller.admin.ordersplit.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY;

@Data
@EqualsAndHashCode(callSuper = true)
public class RdOrderSplitPageReqVO extends PageParam {
    private String splitNo;
    private Long saleOrderId;
    private String saleOrderNo;
    private String customerName;
    private String productModel;
    private String status;
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate[] splitDate;
}
