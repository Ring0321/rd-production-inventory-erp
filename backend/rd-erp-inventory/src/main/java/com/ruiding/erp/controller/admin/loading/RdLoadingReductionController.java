package com.ruiding.erp.controller.admin.loading;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import com.ruiding.erp.controller.admin.loading.vo.*;
import com.ruiding.erp.service.loading.RdLoadingReductionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.*;

@RestController
@Tag(name = "RD ERP - 装车少装商务核定")
@RequestMapping("/rd/loading-task/reduction")
public class RdLoadingReductionController {
    @Resource private RdLoadingReductionService service;

    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('rd:loading-task:dispatch')")
    public CommonResult<RdLoadingReductionRespVO> create(@Valid @RequestBody RdLoadingReductionCreateReqVO req) {
        return success(service.create(req, getLoginUserId(), getLoginUserNickname()));
    }

    @PutMapping("/review")
    @PreAuthorize("@ss.hasPermission('rd:pickup-slip:approve-reduction')")
    public CommonResult<RdLoadingReductionRespVO> review(@Valid @RequestBody RdLoadingReductionReviewReqVO req) {
        return success(service.review(req, getLoginUserId(), getLoginUserNickname()));
    }

    @GetMapping("/list")
    @PreAuthorize("@ss.hasAnyPermissions('rd:loading-task:query','rd:pickup-slip:query')")
    public CommonResult<List<RdLoadingReductionRespVO>> list(@RequestParam(required = false) Long taskId,
                                                           @RequestParam(required = false) Long pickupSlipId) {
        return success(service.list(taskId, pickupSlipId, getLoginUserId()));
    }
    @GetMapping("/get")
    @PreAuthorize("@ss.hasAnyPermissions('rd:loading-task:query','rd:pickup-slip:query')")
    public CommonResult<RdLoadingReductionRespVO> get(@RequestParam(required = false) Long id,
                                                     @RequestParam(required = false) Long taskId,
                                                     @RequestParam(required = false) String idempotencyKey) {
        return success(service.get(id, taskId, idempotencyKey, getLoginUserId()));
    }
}
