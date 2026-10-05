package com.ruiding.erp.controller.admin.loading.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "RD ERP - 装车任务确认出库过账 Response VO")
@Data
public class RdLoadingConfirmOutboundRespVO {

    @Schema(description = "装车任务 ID", example = "1001")
    private Long taskId;

    @Schema(description = "装车任务号", example = "LD202610050001")
    private String taskNo;

    @Schema(description = "生成并过账的出库单 ID")
    private List<Long> outboundOrderIds;

    @Schema(description = "本次出库包数", example = "34")
    private Integer outboundPackageCount;

    @Schema(description = "本次出库重量 kg", example = "34000")
    private BigDecimal outboundWeight;

    @Schema(description = "处理结果说明")
    private String message;
}
