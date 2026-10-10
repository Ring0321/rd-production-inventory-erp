package com.ruiding.erp.service.packageunit;

import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.controller.admin.packageunit.vo.RdPackageTraceRespVO;

import java.util.List;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;

public interface RdPackageUnitService {
    List<RdPackageUnitDO> generate(Long productionOutputId, Integer packageCount);
    void markPrinted(List<Long> packageIds, Long operatorId, String operatorName,
                     boolean reprint, String reprintReason);
    RdPackageUnitDO get(Long id);
    List<RdPackageUnitDO> getByProductionOutputId(Long productionOutputId);
    List<RdPackageUnitDO> trace(String packageCode, Long productionOutputId);
    RdPackageTraceRespVO getTraceDetail(String packageCode, boolean internalView);
    PageResult<RdPackageUnitDO> page(PageParam pageParam);
}
