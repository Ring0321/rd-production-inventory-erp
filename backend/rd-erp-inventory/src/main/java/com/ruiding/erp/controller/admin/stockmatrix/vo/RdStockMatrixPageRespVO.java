package com.ruiding.erp.controller.admin.stockmatrix.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.util.List;

@Data
@Accessors(chain = true)
public class RdStockMatrixPageRespVO {
    private List<LocalDate> dates;
    private List<RdStockMatrixRowRespVO> list;
    private Long total;
    private RdStockMatrixRowRespVO totals;
}
