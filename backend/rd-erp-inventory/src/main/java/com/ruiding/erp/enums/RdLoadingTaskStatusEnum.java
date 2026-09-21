package com.ruiding.erp.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RdLoadingTaskStatusEnum {
    PENDING_ASSIGNMENT("PENDING_ASSIGNMENT"),
    ASSIGNED("ASSIGNED"),
    LOADING("LOADING"),
    PENDING_REVIEW("PENDING_REVIEW"),
    OUTBOUNDED("OUTBOUNDED"),
    EXCEPTION_PENDING("EXCEPTION_PENDING"),
    CANCELLED("CANCELLED");

    private final String status;
}
