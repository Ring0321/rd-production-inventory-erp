package com.ruiding.erp.enums;

/**
 * Order fulfillment lifecycle.
 */
public enum RdOrderFulfillmentStatusEnum {

    MATCHED("MATCHED", "Matched"),
    ALLOCATED("ALLOCATED", "Allocated"),
    OUTBOUNDED("OUTBOUNDED", "Outbounded"),
    CANCELLED("CANCELLED", "Cancelled");

    private final String status;
    private final String name;

    RdOrderFulfillmentStatusEnum(String status, String name) {
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
