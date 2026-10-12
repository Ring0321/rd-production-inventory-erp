package com.ruiding.erp.controller.admin.ai.vo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record RdPackageAiContext(String packageCode, String status, String qualityStage, Source source,
                                 String factsAt, String providerState, List<Notice> notices, Diagnosis diagnosis) {
    public RdPackageAiContext(String packageCode, String status, String qualityStage, Source source,
                             String factsAt, String providerState, List<Notice> notices) {
        this(packageCode, status, qualityStage, source, factsAt, providerState, notices, null);
    }
    public record Source(String state, Integer packageCount, BigDecimal recordedWeight, Map<String, Integer> counts) { }
    public record Notice(String key, String text) { }
    public record Diagnosis(String scope, String state, Integer inspectedPackages, List<Finding> findings) { }
    public record Finding(String key, String severity, String owner, String title, String detail,
                          Integer affectedCount, List<String> packageCodes, String evidence) { }
}
