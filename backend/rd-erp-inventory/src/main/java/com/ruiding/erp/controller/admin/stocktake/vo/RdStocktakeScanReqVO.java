package com.ruiding.erp.controller.admin.stocktake.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@Accessors(chain = true)
public class RdStocktakeScanReqVO {
    @NotNull private Long id;
    @NotBlank private String packageCode;
    private String deviceCode;
}
