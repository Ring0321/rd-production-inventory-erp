package com.ruiding.erp.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RdQualityInspectionStatusEnum {
    DRAFT("DRAFT"), SUBMITTED("SUBMITTED"), PASSED("PASSED"),
    REJECTED("REJECTED"), CORRECTED("CORRECTED"), RESUBMITTED("RESUBMITTED");
    private final String status;
}
