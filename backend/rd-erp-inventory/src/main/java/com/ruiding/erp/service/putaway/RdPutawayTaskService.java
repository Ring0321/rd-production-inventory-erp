package com.ruiding.erp.service.putaway;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.ruiding.erp.controller.admin.putaway.vo.RdPendingPutawayPackagePageReqVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayExceptionCreateReqVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayDriverOptionRespVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayLocationMapRespVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayScanReqVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayScanRespVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayBatchScanPreviewReqVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayBatchScanConfirmReqVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayBatchScanRespVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayTaskConfirmReqVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayTaskCreateReqVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayTaskDetailRespVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayTaskPageReqVO;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayTaskReassignReqVO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayTaskDO;

import java.util.List;

public interface RdPutawayTaskService {
    PageResult<RdPackageUnitDO> getPendingPackagePage(RdPendingPutawayPackagePageReqVO reqVO);

    List<RdPutawayLocationMapRespVO> getLocationMap(String warehouseCode, String areaCode);

    List<RdPutawayDriverOptionRespVO> getDriverOptions();

    Long createTask(RdPutawayTaskCreateReqVO reqVO, Long warehouseKeeperId, String warehouseKeeperName);

    void reassignTask(RdPutawayTaskReassignReqVO reqVO, Long warehouseKeeperId, String warehouseKeeperName);

    RdPutawayScanRespVO scanPackage(RdPutawayScanReqVO reqVO, Long warehouseKeeperId,
                                    String warehouseKeeperName);

    RdPutawayBatchScanRespVO previewBatchScan(RdPutawayBatchScanPreviewReqVO reqVO);

    RdPutawayBatchScanRespVO confirmBatchScan(RdPutawayBatchScanConfirmReqVO reqVO,
                                            Long warehouseKeeperId, String warehouseKeeperName);

    Long reportException(RdPutawayExceptionCreateReqVO reqVO, Long forkliftDriverId,
                         String forkliftDriverName);

    Long confirmTask(RdPutawayTaskConfirmReqVO reqVO, Long warehouseKeeperId,
                     String warehouseKeeperName);

    PageResult<RdPutawayTaskDO> getTaskPage(RdPutawayTaskPageReqVO reqVO);

    PageResult<RdPutawayTaskDO> getMyTaskPage(PageParam pageParam, Long forkliftDriverId);

    RdPutawayTaskDetailRespVO getTaskDetail(Long id);

    RdPutawayTaskDetailRespVO getMyTaskDetail(Long id, Long forkliftDriverId);
}
