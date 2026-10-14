package com.ruiding.erp.controller.admin.loading.vo;

import com.ruiding.erp.dal.dataobject.loading.RdLoadingReductionDO;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class RdLoadingReductionRespVO extends RdLoadingReductionDO {
    private String effectiveStatus;
    private List<Line> lines;

    @Data
    public static class Line {
        private Long id;
        private Integer lineNo;
        private Long pickupSlipLineId;
        private Long packageId;
        private String packageCode;
        private Long saleOrderId;
        private Long fulfillmentId;
        private Long stockLotId;
        private Long plannedLocationId;
        private String plannedLocationCode;
        private String customerName;
        private String productModel;
        private String furnaceNo;
        private String furnaceLayer;
        @JsonFormat(shape = JsonFormat.Shape.STRING) private BigDecimal packageWeight;
        private String lineStatus;
        private Integer planVersion;
        private Integer version;
        private Integer packageVersion;
        private Long claimId;
        private LocalDateTime scanTime;
        private Long outboundOrderId;
        private Long stockTransactionId;
        private boolean removed;
    }
}
