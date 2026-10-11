package com.ruiding.erp.controller.admin.production;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.ruiding.erp.controller.admin.production.vo.*;
import com.ruiding.erp.dal.dataobject.production.RdProductionTaskDO;
import com.ruiding.erp.service.production.RdProductionTaskService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserNickname;

@RestController
@RequestMapping("/rd/production-task")
public class RdProductionTaskController {
    @Resource private RdProductionTaskService service;
    @PostMapping("/create") @PreAuthorize("@ss.hasPermission('rd:production-task:create')")
    public CommonResult<Long> create(@Valid @RequestBody RdProductionTaskSaveReqVO req) {
        return success(service.create(req, getLoginUserId(), getLoginUserNickname()));
    }
    @PutMapping("/update") @PreAuthorize("@ss.hasPermission('rd:production-task:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody RdProductionTaskSaveReqVO req) {
        service.update(req); return success(true);
    }
    @PutMapping("/close") @PreAuthorize("@ss.hasPermission('rd:production-task:close')")
    public CommonResult<Boolean> close(@Valid @RequestBody RdProductionTaskActionReqVO req) {
        service.close(req); return success(true);
    }
    @PutMapping("/cancel") @PreAuthorize("@ss.hasPermission('rd:production-task:cancel')")
    public CommonResult<Boolean> cancel(@Valid @RequestBody RdProductionTaskActionReqVO req) {
        service.cancel(req); return success(true);
    }
    @GetMapping("/get") @PreAuthorize("@ss.hasPermission('rd:production-task:query')")
    public CommonResult<RdProductionTaskDO> get(@RequestParam Long id) { return success(service.get(id)); }
    @GetMapping("/page") @PreAuthorize("@ss.hasPermission('rd:production-task:query')")
    public CommonResult<PageResult<RdProductionTaskDO>> page(@Valid RdProductionTaskPageReqVO req) { return success(service.page(req)); }
}
