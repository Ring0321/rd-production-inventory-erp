package com.ruiding.erp.controller.admin.putaway.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RdPutawayLocationMapRespVO {
    private Long id;
    private String warehouseCode;
    private String warehouseName;
    private String areaCode;
    private String areaName;
    private String rowCode;
    private String columnCode;
    private String levelCode;
    private String locationCode;
    private String locationName;
    private BigDecimal capacityPackages;
    private BigDecimal capacityWeight;
    private BigDecimal occupiedPackages;
    private BigDecimal occupiedWeight;
    private BigDecimal reservedPackages;
    private BigDecimal reservedWeight;
    private BigDecimal availablePackages;
    private BigDecimal availableWeight;
    private BigDecimal usagePercent;
    private Boolean frozen;
    private Boolean enabled;
    private String locationStatus;
    private String displayStatus;
}
