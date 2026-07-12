package com.ruiding.erp.controller.admin.stocktake.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class RdStocktakeDetailRespVO {
    private RdStocktakeOrderRespVO order;
    private List<RdStocktakeLineRespVO> lines;
}
