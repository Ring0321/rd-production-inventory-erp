package com.ruiding.erp.controller.admin.weight.vo;

import com.ruiding.erp.service.weight.RdWeightSolver;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class RdWeightSuggestRespVO {
    private Long pickupSlipId;
    private Long pickupSlipLineId;
    private String slipNo;
    private String customerName;
    private String productModel;
    private BigDecimal authorizedWeightKg;
    private Integer authorizedPackageCount;
    private BigDecimal targetWeightKg;
    private BigDecimal toleranceKg;
    private String solverStatus;
    private String message;
    private boolean withinTolerance;
    private BigDecimal totalWeightKg;
    private BigDecimal deviationKg;
    private BigDecimal shortfallKg;
    private BigDecimal availableWeightUpperBoundKg;
    private Double objectiveBound;
    private Integer candidateCount;
    private int scannedCount;
    private boolean candidateScanComplete;
    private int candidateLimit = 500;
    private int solverTimeLimitMs = 2000;
    private long elapsedMs;
    private String factsAt;
    private int weightScale = 3;
    private List<RdWeightSolver.Bag> bags = List.of();
}
