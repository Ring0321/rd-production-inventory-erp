package com.ruiding.erp.enums;

/**
 * Transfer order lifecycle.
 */
public enum RdTransferOrderStatusEnum {

    DRAFT("DRAFT", "Draft"),
    CONFIRMED("CONFIRMED", "Confirmed"),
    CANCELLED("CANCELLED", "Cancelled");

    private final String status;
    private final String name;

    RdTransferOrderStatusEnum(String status, String name) {
        this.status = status;
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public String getName() {
        return name;
    }

}
