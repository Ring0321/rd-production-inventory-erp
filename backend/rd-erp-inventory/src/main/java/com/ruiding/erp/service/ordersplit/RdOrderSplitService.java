package com.ruiding.erp.service.ordersplit;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.ruiding.erp.controller.admin.ordersplit.vo.*;
import com.ruiding.erp.dal.dataobject.ordersplit.RdOrderSplitResultDO;
import com.ruiding.erp.dal.dataobject.ordersplit.RdOrderSplitRuleDO;
import com.ruiding.erp.dal.dataobject.production.RdProductionOutputDO;

import java.util.List;
import java.util.Map;

public interface RdOrderSplitService {
    Long createRule(RdOrderSplitRuleSaveReqVO reqVO);
    void updateRule(RdOrderSplitRuleSaveReqVO reqVO);
    Map<String, Object> getRule(Long id);
    PageResult<RdOrderSplitRuleDO> getRulePage(RdOrderSplitRulePageReqVO reqVO);
    Long create(RdOrderSplitCreateReqVO reqVO);
    void adjust(RdOrderSplitAdjustReqVO reqVO);
    void confirm(RdOrderSplitActionReqVO reqVO, Long userId, String userName);
    void cancel(RdOrderSplitActionReqVO reqVO, Long userId, String userName);
    void bindOutput(RdOrderSplitBindReqVO reqVO, Long userId, String userName);
    void unbindOutput(RdOrderSplitUnbindReqVO reqVO, Long userId, String userName);
    RdOrderSplitDetailRespVO getDetail(Long id);
    PageResult<RdOrderSplitResultDO> getPage(RdOrderSplitPageReqVO reqVO);
    List<RdProductionOutputDO> getProductionOptions(Long resultLineId);
}
