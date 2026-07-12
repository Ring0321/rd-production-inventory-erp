package com.ruiding.erp.service.quality;

import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionDO;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionItemDO;

import java.util.List;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;

public interface RdQualityInspectionService {
    Long create(Long packageId, List<RdQualityInspectionItemDO> items, String remark);
    void submit(Long id, Long operatorId, String operatorName);
    void approve(Long id, Long reviewerId, String reviewerName);
    void reject(Long id, Long reviewerId, String reviewerName, String reason);
    RdQualityInspectionDO get(Long id);
    PageResult<RdQualityInspectionDO> page(PageParam pageParam);
}
