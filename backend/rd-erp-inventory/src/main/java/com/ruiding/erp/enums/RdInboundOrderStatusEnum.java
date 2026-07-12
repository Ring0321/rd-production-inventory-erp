package com.ruiding.erp.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RdInboundOrderStatusEnum {
    DRAFT("DRAFT"), SUBMITTED("SUBMITTED"), VERIFIED("VERIFIED"),
    POSTED("POSTED"), REVERSED("REVERSED");
    private final String status;
}
