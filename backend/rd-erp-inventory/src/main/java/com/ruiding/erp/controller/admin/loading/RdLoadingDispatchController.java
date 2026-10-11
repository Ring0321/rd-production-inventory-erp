package com.ruiding.erp.controller.admin.loading;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.ruiding.erp.controller.admin.common.vo.RdBatchScanStatusRespVO;
import com.ruiding.erp.controller.admin.loading.vo.*;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayDriverOptionRespVO;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingTaskDO;
import com.ruiding.erp.dal.dataobject.loading.RdPickupSlipDO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.service.loading.RdLoadingDispatchService;
import com.ruiding.erp.service.putaway.RdPutawayTaskService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserNickname;

@Tag(name = "RD ERP - 提货单与装车派单")
@RestController
@RequestMapping("/rd/loading-task")
public class RdLoadingDispatchController {
    @Resource private RdLoadingDispatchService service;
    @Resource private RdPutawayTaskService putawayService;

    @PostMapping("/pickup-slip/create")
    @PreAuthorize("@ss.hasPermission('rd:pickup-slip:create')")
    public CommonResult<Long> createPickupSlip(@Valid @RequestBody RdPickupSlipCreateReqVO req) {
        return success(service.createPickupSlip(req, getLoginUserId(), getLoginUserNickname()));
    }

    @GetMapping("/pickup-slip/page")
    @PreAuthorize("@ss.hasPermission('rd:pickup-slip:query')")
    public CommonResult<PageResult<RdPickupSlipDO>> getPickupSlipPage(@Valid RdPickupSlipPageReqVO req) {
        return success(service.getPickupSlipPage(req));
    }

    @GetMapping("/pickup-slip/get")
    @PreAuthorize("@ss.hasPermission('rd:pickup-slip:query')")
    public CommonResult<RdPickupSlipDetailRespVO> getPickupSlip(@RequestParam Long id) {
        return success(service.getPickupSlip(id));
    }

    @GetMapping("/available-package-page")
    @PreAuthorize("@ss.hasPermission('rd:loading-task:query')")
    public CommonResult<PageResult<RdPackageUnitDO>> getAvailablePackagePage(@Valid RdLoadingPackagePageReqVO req) {
        return success(service.getAvailablePackagePage(req));
    }

    @GetMapping("/driver-options")
    @PreAuthorize("@ss.hasPermission('rd:loading-task:dispatch')")
    public CommonResult<List<RdPutawayDriverOptionRespVO>> getDriverOptions() {
        return success(putawayService.getDriverOptions());
    }

    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('rd:loading-task:dispatch')")
    public CommonResult<Long> createTask(@Valid @RequestBody RdLoadingTaskCreateReqVO req) {
        return success(service.createTask(req, getLoginUserId(), getLoginUserNickname()));
    }

    @PostMapping("/batch-scan/preview")
    @PreAuthorize("@ss.hasPermission('rd:loading-task:dispatch')")
    public CommonResult<RdLoadingBatchScanRespVO> previewBatchScan(
            @Valid @RequestBody RdLoadingBatchScanPreviewReqVO req) {
        return success(service.previewBatchScan(req));
    }

    @PostMapping("/batch-scan/confirm")
    @PreAuthorize("@ss.hasPermission('rd:loading-task:dispatch')")
    public CommonResult<RdLoadingBatchScanRespVO> confirmBatchScan(
            @Valid @RequestBody RdLoadingBatchScanConfirmReqVO req) {
        return success(service.confirmBatchScan(req, getLoginUserId(), getLoginUserNickname()));
    }

    @PostMapping("/batch-scan/status")
    @PreAuthorize("@ss.hasPermission('rd:loading-task:dispatch')")
    public CommonResult<RdBatchScanStatusRespVO> getBatchScanStatus(
            @Valid @RequestBody RdLoadingBatchScanConfirmReqVO req) {
        return success(service.getBatchScanStatus(req, getLoginUserId()));
    }

    @PostMapping("/confirm-outbound")
    @PreAuthorize("@ss.hasPermission('rd:loading-task:dispatch')")
    public CommonResult<RdLoadingConfirmOutboundRespVO> confirmOutbound(
            @Valid @RequestBody RdLoadingConfirmOutboundReqVO req) {
        return success(service.confirmOutbound(req, getLoginUserId(), getLoginUserNickname()));
    }

    @PostMapping("/exception")
    @PreAuthorize("@ss.hasPermission('rd:loading-task:execute')")
    public CommonResult<Long> reportException(@Valid @RequestBody RdLoadingExceptionCreateReqVO req) {
        return success(service.reportException(req, getLoginUserId(), getLoginUserNickname()));
    }

    @PutMapping("/exception/resolve")
    @PreAuthorize("@ss.hasPermission('rd:loading-task:dispatch')")
    public CommonResult<Boolean> resolveException(@Valid @RequestBody RdLoadingExceptionResolveReqVO req) {
        service.resolveException(req, getLoginUserId(), getLoginUserNickname());
        return success(true);
    }

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('rd:loading-task:query')")
    public CommonResult<PageResult<RdLoadingTaskDO>> getTaskPage(@Valid RdLoadingTaskPageReqVO req) {
        return success(service.getTaskPage(req, null));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('rd:loading-task:query')")
    public CommonResult<RdLoadingTaskDetailRespVO> getTask(@RequestParam Long id) {
        return success(service.getTask(id, null));
    }

    @GetMapping("/my-page")
    @PreAuthorize("@ss.hasPermission('rd:loading-task:execute')")
    public CommonResult<PageResult<RdLoadingTaskDO>> getMyTaskPage(@Valid RdLoadingTaskPageReqVO req) {
        return success(service.getTaskPage(req, getLoginUserId()));
    }

    @GetMapping("/my-get")
    @PreAuthorize("@ss.hasPermission('rd:loading-task:execute')")
    public CommonResult<RdLoadingTaskDetailRespVO> getMyTask(@RequestParam Long id) {
        return success(service.getTask(id, getLoginUserId()));
    }
}
