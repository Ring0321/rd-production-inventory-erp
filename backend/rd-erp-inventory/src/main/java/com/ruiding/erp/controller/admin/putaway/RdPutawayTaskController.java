package com.ruiding.erp.controller.admin.putaway;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
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
import com.ruiding.erp.service.putaway.RdPutawayTaskService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserNickname;

@Tag(name = "RD ERP - 入库上架任务")
@RestController
@RequestMapping("/rd/putaway-task")
public class RdPutawayTaskController {
    @Resource private RdPutawayTaskService service;

    @GetMapping("/pending-package-page")
    @PreAuthorize("@ss.hasPermission('rd:putaway-task:query')")
    public CommonResult<PageResult<RdPackageUnitDO>> getPendingPackagePage(
            @Valid RdPendingPutawayPackagePageReqVO reqVO) {
        return success(service.getPendingPackagePage(reqVO));
    }

    @GetMapping("/location-map")
    @PreAuthorize("@ss.hasPermission('rd:putaway-task:query')")
    public CommonResult<List<RdPutawayLocationMapRespVO>> getLocationMap(
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) String areaCode) {
        return success(service.getLocationMap(warehouseCode, areaCode));
    }

    @GetMapping("/driver-options")
    @PreAuthorize("@ss.hasPermission('rd:putaway-task:create')")
    public CommonResult<List<RdPutawayDriverOptionRespVO>> getDriverOptions() {
        return success(service.getDriverOptions());
    }

    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('rd:putaway-task:create')")
    public CommonResult<Long> createTask(@Valid @RequestBody RdPutawayTaskCreateReqVO reqVO) {
        return success(service.createTask(reqVO, getLoginUserId(), getLoginUserNickname()));
    }

    @PutMapping("/reassign")
    @PreAuthorize("@ss.hasPermission('rd:putaway-task:assign')")
    public CommonResult<Boolean> reassignTask(@Valid @RequestBody RdPutawayTaskReassignReqVO reqVO) {
        service.reassignTask(reqVO, getLoginUserId(), getLoginUserNickname());
        return success(true);
    }

    @PostMapping("/scan")
    @PreAuthorize("@ss.hasPermission('rd:putaway-task:confirm')")
    public CommonResult<RdPutawayScanRespVO> scanPackage(
            @Valid @RequestBody RdPutawayScanReqVO reqVO) {
        return success(service.scanPackage(reqVO, getLoginUserId(), getLoginUserNickname()));
    }

    @PostMapping("/batch-scan/preview")
    @PreAuthorize("@ss.hasPermission('rd:putaway-task:query')")
    public CommonResult<RdPutawayBatchScanRespVO> previewBatchScan(
            @Valid @RequestBody RdPutawayBatchScanPreviewReqVO reqVO) {
        return success(service.previewBatchScan(reqVO));
    }

    @PostMapping("/batch-scan/confirm")
    @PreAuthorize("@ss.hasPermission('rd:putaway-task:confirm')")
    public CommonResult<RdPutawayBatchScanRespVO> confirmBatchScan(
            @Valid @RequestBody RdPutawayBatchScanConfirmReqVO reqVO) {
        return success(service.confirmBatchScan(reqVO, getLoginUserId(), getLoginUserNickname()));
    }

    @PostMapping("/exception")
    @PreAuthorize("@ss.hasPermission('rd:putaway-task:execute')")
    public CommonResult<Long> reportException(
            @Valid @RequestBody RdPutawayExceptionCreateReqVO reqVO) {
        return success(service.reportException(reqVO, getLoginUserId(), getLoginUserNickname()));
    }

    @PutMapping("/confirm")
    @PreAuthorize("@ss.hasPermission('rd:putaway-task:confirm')")
    public CommonResult<Long> confirmTask(@Valid @RequestBody RdPutawayTaskConfirmReqVO reqVO) {
        return success(service.confirmTask(reqVO, getLoginUserId(), getLoginUserNickname()));
    }

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('rd:putaway-task:query')")
    public CommonResult<PageResult<RdPutawayTaskDO>> getTaskPage(@Valid RdPutawayTaskPageReqVO reqVO) {
        return success(service.getTaskPage(reqVO));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('rd:putaway-task:query')")
    public CommonResult<RdPutawayTaskDetailRespVO> getTaskDetail(@RequestParam Long id) {
        return success(service.getTaskDetail(id));
    }

    @GetMapping("/my-page")
    @PreAuthorize("@ss.hasPermission('rd:putaway-task:execute')")
    public CommonResult<PageResult<RdPutawayTaskDO>> getMyTaskPage(@Valid PageParam pageParam) {
        return success(service.getMyTaskPage(pageParam, getLoginUserId()));
    }

    @GetMapping("/my-get")
    @PreAuthorize("@ss.hasPermission('rd:putaway-task:execute')")
    public CommonResult<RdPutawayTaskDetailRespVO> getMyTaskDetail(@RequestParam Long id) {
        return success(service.getMyTaskDetail(id, getLoginUserId()));
    }
}
