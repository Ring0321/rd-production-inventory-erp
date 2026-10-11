package com.ruiding.erp.controller.admin.ai.vo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record RdPackageAiContext(String packageCode, String status, String qualityStage, Source source,
                                 String factsAt, String providerState, List<Notice> notices) {
    public record Source(String state, Integer packageCount, BigDecimal recordedWeight, Map<String, Integer> counts) { }
    public record Notice(String key, String text) { }
}
