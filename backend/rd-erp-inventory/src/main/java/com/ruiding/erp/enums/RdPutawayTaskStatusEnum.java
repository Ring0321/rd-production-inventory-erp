package com.ruiding.erp.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RdPutawayTaskStatusEnum {
    PENDING_PUTAWAY("PENDING_PUTAWAY"),
    ASSIGNED("ASSIGNED"),
    MOVING("MOVING"),
    PENDING_CONFIRMATION("PENDING_CONFIRMATION"),
    PUTAWAY_COMPLETED("PUTAWAY_COMPLETED"),
    EXCEPTION_PENDING("EXCEPTION_PENDING"),
    CANCELLED("CANCELLED");

    private final String status;
}
