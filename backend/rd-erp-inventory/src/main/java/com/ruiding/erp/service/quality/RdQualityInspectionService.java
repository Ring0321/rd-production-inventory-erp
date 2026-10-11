package com.ruiding.erp.service.quality;

import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionDO;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionItemDO;
import com.ruiding.erp.controller.admin.quality.vo.RdQualityInspectionBatchCreateReqVO;
import com.ruiding.erp.controller.admin.quality.vo.RdQualityInspectionDetailRespVO;
import com.ruiding.erp.controller.admin.quality.vo.RdQualityInspectionScopeReqVO;
import com.ruiding.erp.controller.admin.quality.vo.RdQualityInspectionScopeRespVO;

import java.util.List;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;

public interface RdQualityInspectionService {
    Long create(Long packageId, List<RdQualityInspectionItemDO> items, String remark);
    Long createBatch(RdQualityInspectionBatchCreateReqVO req);
    RdQualityInspectionScopeRespVO scope(RdQualityInspectionScopeReqVO req);
    RdQualityInspectionDetailRespVO getDetail(Long id);
    void submit(Long id, Long operatorId, String operatorName);
    void approve(Long id, Long reviewerId, String reviewerName);
    void reject(Long id, Long reviewerId, String reviewerName, String reason);
    RdQualityInspectionDO get(Long id);
    PageResult<RdQualityInspectionDO> page(PageParam pageParam);
}
