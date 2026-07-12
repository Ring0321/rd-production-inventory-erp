package com.ruiding.erp.service.packageunit;

public interface RdScanEventService {
    void record(String bizType, Long bizId, Long packageId, String packageCode, String deviceCode,
                Long operatorId, String operatorName, boolean success, String errorMessage);
}
