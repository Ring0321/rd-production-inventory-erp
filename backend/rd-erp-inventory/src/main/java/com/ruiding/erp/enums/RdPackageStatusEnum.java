package com.ruiding.erp.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RdPackageStatusEnum {
    CREATED("CREATED"), LABELED("LABELED"), QC_PENDING("QC_PENDING"),
    QC_PASSED("QC_PASSED"), QC_REJECTED("QC_REJECTED"),
    INBOUND_PENDING("INBOUND_PENDING"), IN_STOCK("IN_STOCK"),
    OUTBOUND_PENDING("OUTBOUND_PENDING"), OUTBOUNDED("OUTBOUNDED"), VOIDED("VOIDED");

    private final String status;
}
