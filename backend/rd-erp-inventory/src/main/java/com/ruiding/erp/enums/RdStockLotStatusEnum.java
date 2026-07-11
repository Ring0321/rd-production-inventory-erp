package com.ruiding.erp.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * RD stock lot status.
 */
@Getter
@AllArgsConstructor
public enum RdStockLotStatusEnum {

    AVAILABLE("AVAILABLE", "可用"),
    LOCKED("LOCKED", "锁定"),
    EMPTY("EMPTY", "已空"),
    CLOSED("CLOSED", "已关闭");

    private final String status;
    private final String name;

}
