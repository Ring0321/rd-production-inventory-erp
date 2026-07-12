package com.ruiding.erp.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RdStocktakeOrderStatusEnum {
    DRAFT("DRAFT"),
    FROZEN("FROZEN"),
    COUNTING("COUNTING"),
    SUBMITTED("SUBMITTED"),
    APPROVED("APPROVED"),
    ADJUSTED("ADJUSTED"),
    CLOSED("CLOSED"),
    CANCELLED("CANCELLED");

    private final String status;
}
