package com.ruiding.erp.enums;

/**
 * Outbound order lifecycle.
 */
public enum RdOutboundOrderStatusEnum {

    DRAFT("DRAFT", "Draft"),
    CONFIRMED("CONFIRMED", "Confirmed"),
    CANCELLED("CANCELLED", "Cancelled");

    private final String status;
    private final String name;

    RdOutboundOrderStatusEnum(String status, String name) {
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
