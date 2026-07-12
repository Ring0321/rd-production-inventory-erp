package com.ruiding.erp.service.packageunit;

import com.ruiding.erp.dal.dataobject.packageunit.RdScanEventDO;
import com.ruiding.erp.dal.mysql.packageunit.RdScanEventMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Service
public class RdScanEventServiceImpl implements RdScanEventService {
    @Resource private RdScanEventMapper scanEventMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String bizType, Long bizId, Long packageId, String packageCode, String deviceCode,
                       Long operatorId, String operatorName, boolean success, String errorMessage) {
        scanEventMapper.insert(new RdScanEventDO().setBizType(bizType).setBizId(bizId)
                .setPackageId(packageId).setPackageCode(packageCode).setDeviceCode(deviceCode)
                .setOperatorId(operatorId).setOperatorName(operatorName).setSuccess(success)
                .setErrorMessage(errorMessage).setScanTime(LocalDateTime.now()));
    }
}
