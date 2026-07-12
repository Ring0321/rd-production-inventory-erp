package com.ruiding.erp.controller.admin.ordersplit.vo;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

@Data
public class RdOrderSplitAdjustReqVO {
    @NotNull private Long id;
    @NotNull private Integer version;
    @Valid @NotEmpty private List<Line> lines;

    @Data
    public static class Line {
        @NotNull private Long lineId;
        @NotNull private BigDecimal manualAdjustmentQty;
        private String adjustmentReason;
    }
}
