package com.ruiding.erp.service.masterdata;

import com.ruiding.erp.dal.dataobject.masterdata.RdWarehouseLocationDO;
import com.ruiding.erp.dal.mysql.masterdata.RdWarehouseLocationMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.ruiding.erp.enums.ErrorCodeConstants.WAREHOUSE_LOCATION_FROZEN;
import static com.ruiding.erp.enums.ErrorCodeConstants.WAREHOUSE_LOCATION_NOT_EXISTS;

@Service
public class RdWarehouseLocationFreezeGuardImpl implements RdWarehouseLocationFreezeGuard {

    @Resource
    private RdWarehouseLocationMapper locationMapper;

    @Override
    public void requireMovable(Long locationId) {
        RdWarehouseLocationDO location = locationId == null ? null : locationMapper.selectByIdForUpdate(locationId);
        if (location == null || !Boolean.TRUE.equals(location.getEnabled())) {
            throw exception(WAREHOUSE_LOCATION_NOT_EXISTS);
        }
        if (Boolean.TRUE.equals(location.getFrozen())) {
            throw exception(WAREHOUSE_LOCATION_FROZEN);
        }
    }
}
