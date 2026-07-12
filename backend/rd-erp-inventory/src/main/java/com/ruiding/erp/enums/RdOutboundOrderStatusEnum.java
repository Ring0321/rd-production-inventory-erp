package com.ruiding.erp.enums;

/**
 * Outbound order lifecycle.
 */
public enum RdOutboundOrderStatusEnum {

    DRAFT("DRAFT", "Draft"),
    SCANNING("SCANNING", "Scanning"),
    SUBMITTED("SUBMITTED", "Submitted"),
    APPROVED("APPROVED", "Approved"),
    POSTED("POSTED", "Posted"),
    REVERSED("REVERSED", "Reversed"),
    CANCELLED("CANCELLED", "Cancelled"),
    CONFIRMED("CONFIRMED", "Legacy confirmed");

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
