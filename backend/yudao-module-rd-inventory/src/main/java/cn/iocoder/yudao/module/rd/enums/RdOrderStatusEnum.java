package cn.iocoder.yudao.module.rd.enums;

/**
 * Order lifecycle used by RD inventory fulfillment.
 */
public enum RdOrderStatusEnum {

    OPEN("OPEN", "Open"),
    PARTIAL("PARTIAL", "Partial"),
    FULFILLED("FULFILLED", "Fulfilled"),
    CLOSED("CLOSED", "Closed"),
    CANCELLED("CANCELLED", "Cancelled");

    private final String status;
    private final String name;

    RdOrderStatusEnum(String status, String name) {
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
