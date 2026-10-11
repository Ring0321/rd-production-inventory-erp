package com.ruiding.erp.controller.admin.ai.vo;

import java.util.List;

public record RdPackageAiAnswer(String requestId, String state, String message, RdPackageAiContext context,
                                List<String> noteKeys, Integer totalTokens, String answeredAt) { }
