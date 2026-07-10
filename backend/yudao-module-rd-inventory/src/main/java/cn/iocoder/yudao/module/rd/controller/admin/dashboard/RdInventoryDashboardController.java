package cn.iocoder.yudao.module.rd.controller.admin.dashboard;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.rd.controller.admin.dashboard.vo.RdInventoryDashboardRespVO;
import cn.iocoder.yudao.module.rd.service.dashboard.RdInventoryDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.time.LocalDate;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY;

@Tag(name = "管理后台 - 瑞鼎库存看板")
@RestController
@RequestMapping("/rd/dashboard")
public class RdInventoryDashboardController {

    @Resource
    private RdInventoryDashboardService dashboardService;

    @GetMapping("/overview")
    @Operation(summary = "获得库存与订单看板")
    @PreAuthorize("@ss.hasPermission('rd:dashboard:query')")
    public CommonResult<RdInventoryDashboardRespVO> getOverview(
            @RequestParam(value = "asOfDate", required = false)
            @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY) LocalDate asOfDate) {
        return success(dashboardService.getOverview(asOfDate));
    }

}
