package com.ruiding.erp.controller.admin.ordersplit.vo;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

@Data
public class RdOrderSplitRuleSaveReqVO {
    private Long id;
    private Integer version;
    @NotBlank private String ruleCode;
    @NotBlank private String ruleName;
    @NotBlank private String customerName;
    @NotBlank private String productModel;
    @NotNull @DecimalMin("0.000001") private BigDecimal defaultFurnaceLoadQty;
    @NotNull @DecimalMin("0.000001") @DecimalMax("100") private BigDecimal yieldRatePercent;
    @NotNull private Boolean enabled;
    private String remark;
    @Valid @NotEmpty private List<Line> lines;

    @Data
    public static class Line {
        @NotNull private Integer sequenceNo;
        @NotBlank private String furnaceLayer;
        @NotBlank private String allocationType;
        @NotNull @DecimalMin("0.000001") private BigDecimal allocationValue;
        private String remark;
    }
}
