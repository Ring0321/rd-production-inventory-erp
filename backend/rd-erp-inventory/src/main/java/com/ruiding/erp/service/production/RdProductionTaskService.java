package com.ruiding.erp.service.production;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.ruiding.erp.controller.admin.production.vo.RdProductionTaskSaveReqVO;
import com.ruiding.erp.controller.admin.production.vo.RdProductionTaskPageReqVO;
import com.ruiding.erp.controller.admin.production.vo.RdProductionTaskActionReqVO;
import com.ruiding.erp.dal.dataobject.production.RdProductionTaskDO;

public interface RdProductionTaskService {
    Long create(RdProductionTaskSaveReqVO req, Long userId, String userName);
    void update(RdProductionTaskSaveReqVO req);
    void close(RdProductionTaskActionReqVO req);
    void cancel(RdProductionTaskActionReqVO req);
    RdProductionTaskDO get(Long id);
    PageResult<RdProductionTaskDO> page(RdProductionTaskPageReqVO req);
}
