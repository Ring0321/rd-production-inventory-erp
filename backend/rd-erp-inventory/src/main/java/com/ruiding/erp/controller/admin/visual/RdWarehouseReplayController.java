package com.ruiding.erp.controller.admin.visual;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import com.ruiding.erp.service.visual.RdWarehouseReplayService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController @RequestMapping("/rd/warehouse-replay")
public class RdWarehouseReplayController {
    @Resource private RdWarehouseReplayService service;
    @GetMapping("/task")
    @PreAuthorize("@ss.hasPermission('rd:stock-transaction:query')")
    public CommonResult<RdWarehouseReplayService.View> task(@RequestParam Long productionTaskId) {
        return success(service.task(productionTaskId));
    }
}
