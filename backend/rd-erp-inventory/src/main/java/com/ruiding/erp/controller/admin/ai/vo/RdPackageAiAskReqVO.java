package com.ruiding.erp.controller.admin.ai.vo;

import lombok.Data;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

@Data
public class RdPackageAiAskReqVO {
    @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{0,63}") private String packageCode;
    @NotNull private Question question;
    @NotNull @Pattern(regexp = "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}") private String requestId;
    public enum Question { STATUS, SOURCE_PROGRESS, NEXT_STEP, RISK_REVIEW, HANDOVER }
}
