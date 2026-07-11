package com.ruiding.erp.enums;

/**
 * Inventory ledger movement types.
 */
public enum RdStockTransactionTypeEnum {

    OPENING("OPENING", "Opening"),
    PRODUCTION_IN("PRODUCTION_IN", "Production inbound"),
    INBOUND("INBOUND", "Inbound"),
    OUTBOUND("OUTBOUND", "Outbound"),
    TRANSFER_IN("TRANSFER_IN", "Transfer inbound"),
    TRANSFER_OUT("TRANSFER_OUT", "Transfer outbound"),
    RETURN_IN("RETURN_IN", "Return inbound"),
    ADJUST_IN("ADJUST_IN", "Adjustment inbound"),
    ADJUST_OUT("ADJUST_OUT", "Adjustment outbound");

    private final String type;
    private final String name;

    RdStockTransactionTypeEnum(String type, String name) {
        this.type = type;
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

}
