package com.ruiding.erp.service.loading;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.ruiding.erp.controller.admin.loading.vo.RdLoadingPackagePageReqVO;
import com.ruiding.erp.controller.admin.loading.vo.RdLoadingTaskCreateReqVO;
import com.ruiding.erp.controller.admin.loading.vo.RdLoadingTaskDetailRespVO;
import com.ruiding.erp.controller.admin.loading.vo.RdLoadingTaskPageReqVO;
import com.ruiding.erp.controller.admin.loading.vo.RdPickupSlipCreateReqVO;
import com.ruiding.erp.controller.admin.loading.vo.RdPickupSlipDetailRespVO;
import com.ruiding.erp.controller.admin.loading.vo.RdPickupSlipPageReqVO;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingTaskDO;
import com.ruiding.erp.dal.dataobject.loading.RdPickupSlipDO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;

public interface RdLoadingDispatchService {
    Long createPickupSlip(RdPickupSlipCreateReqVO req, Long issuerId, String issuerName);
    PageResult<RdPickupSlipDO> getPickupSlipPage(RdPickupSlipPageReqVO req);
    RdPickupSlipDetailRespVO getPickupSlip(Long id);
    PageResult<RdPackageUnitDO> getAvailablePackagePage(RdLoadingPackagePageReqVO req);
    Long createTask(RdLoadingTaskCreateReqVO req, Long keeperId, String keeperName);
    PageResult<RdLoadingTaskDO> getTaskPage(RdLoadingTaskPageReqVO req, Long driverId);
    RdLoadingTaskDetailRespVO getTask(Long id, Long driverId);
}
