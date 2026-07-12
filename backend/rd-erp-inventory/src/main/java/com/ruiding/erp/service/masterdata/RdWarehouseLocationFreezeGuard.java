package com.ruiding.erp.service.masterdata;

public interface RdWarehouseLocationFreezeGuard {

    void requireMovable(Long locationId);
}
