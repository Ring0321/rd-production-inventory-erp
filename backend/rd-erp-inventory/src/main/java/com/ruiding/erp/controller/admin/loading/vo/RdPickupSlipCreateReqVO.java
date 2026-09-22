package com.ruiding.erp.controller.admin.loading.vo;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class RdPickupSlipCreateReqVO {
    @NotBlank private String slipNo;
    @NotBlank private String customerName;
    private LocalDate pickupDate;
    private String vehiclePlate;
    private String transportDriverName;
    private String transportDriverPhone;
    private Integer requestedPackageCount;
    private BigDecimal requestedWeight;
    private String sourceAttachmentRef;
    private String remark;
    @Valid @NotEmpty private List<Line> lines;

    @Data
    public static class Line {
        @NotNull private Integer lineNo;
        private Long saleOrderId;
        private String productName;
        @NotBlank private String productModel;
        private String furnaceNo;
        private String furnaceLayer;
        private Integer requestedPackageCount;
        private BigDecimal requestedWeight;
        private String remark;
    }
}
