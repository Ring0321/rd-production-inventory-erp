package com.ruiding.erp.service.packageunit;

import com.ruiding.erp.dal.dataobject.packageunit.RdPackageTaskEventDO;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageTaskEventMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Objects;

@Service
public class RdPackageTaskHistoryService {
    @Resource private RdPackageTaskEventMapper eventMapper;

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void append(RdPackageTaskEventDO event) {
        Objects.requireNonNull(event.getPackageId(), "packageId");
        Objects.requireNonNull(event.getPackageCode(), "packageCode");
        Objects.requireNonNull(event.getStage(), "stage");
        Objects.requireNonNull(event.getEventCode(), "eventCode");
        Objects.requireNonNull(event.getSourceType(), "sourceType");
        Objects.requireNonNull(event.getSourceId(), "sourceId");
        Objects.requireNonNull(event.getSourceNo(), "sourceNo");
        Objects.requireNonNull(event.getSourceVersion(), "sourceVersion");
        Objects.requireNonNull(event.getEventTime(), "eventTime");
        eventMapper.insert(event);
    }
}
