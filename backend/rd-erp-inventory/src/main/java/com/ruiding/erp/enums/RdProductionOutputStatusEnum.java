package com.ruiding.erp.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * RD production output status.
 */
@Getter
@AllArgsConstructor
public enum RdProductionOutputStatusEnum {

    DRAFT("DRAFT", "草稿"),
    CONFIRMED("CONFIRMED", "已确认"),
    INBOUNDED("INBOUNDED", "已入库"),
    CANCELLED("CANCELLED", "已作废");

    private final String status;
    private final String name;

    public static boolean isValid(String status) {
        return Arrays.stream(values()).anyMatch(item -> item.getStatus().equals(status));
    }

}
