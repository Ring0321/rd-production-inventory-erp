package com.ruiding.erp.controller.admin.stocktake.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
@Accessors(chain = true)
public class RdStocktakeCreateReqVO {
    @NotNull private Long locationId;
    @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate stocktakeDate;
    private String remark;
}
