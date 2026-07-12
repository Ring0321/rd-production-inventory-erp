package com.ruiding.erp.service.packageunit;

import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;

import java.util.List;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;

public interface RdPackageUnitService {
    List<RdPackageUnitDO> generate(Long productionOutputId, Integer packageCount);
    void markPrinted(Long packageId, Long operatorId, String operatorName, String reprintReason);
    RdPackageUnitDO get(Long id);
    List<RdPackageUnitDO> getByProductionOutputId(Long productionOutputId);
    List<RdPackageUnitDO> trace(String packageCode, Long productionOutputId);
    PageResult<RdPackageUnitDO> page(PageParam pageParam);
}
