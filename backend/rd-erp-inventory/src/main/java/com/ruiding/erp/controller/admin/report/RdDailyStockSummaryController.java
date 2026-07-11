package com.ruiding.erp.controller.admin.report;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import com.ruiding.erp.controller.admin.report.vo.RdDailyStockSummaryPageReqVO;
import com.ruiding.erp.controller.admin.report.vo.RdDailyStockSummaryRespVO;
import com.ruiding.erp.controller.admin.report.vo.RdDailyStockTrendRespVO;
import com.ruiding.erp.dal.dataobject.report.RdDailyStockSummaryDO;
import com.ruiding.erp.service.report.RdDailyStockSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.EXPORT;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY;

@Tag(name = "管理后台 - RD 每日库存汇总")
@RestController
@RequestMapping("/rd/daily-stock-summary")
@Validated
public class RdDailyStockSummaryController {

    @Resource
    private RdDailyStockSummaryService dailyStockSummaryService;

    @GetMapping("/page")
    @Operation(summary = "获得每日库存汇总分页")
    @PreAuthorize("@ss.hasPermission('rd:daily-stock-summary:query')")
    public CommonResult<PageResult<RdDailyStockSummaryRespVO>> getPage(
            @Valid RdDailyStockSummaryPageReqVO pageReqVO) {
        PageResult<RdDailyStockSummaryDO> pageResult = dailyStockSummaryService.getPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, RdDailyStockSummaryRespVO.class));
    }

    @GetMapping("/trend")
    @Operation(summary = "获得每日库存变化趋势")
    @PreAuthorize("@ss.hasPermission('rd:daily-stock-summary:query')")
    public CommonResult<List<RdDailyStockTrendRespVO>> getTrend(
            @Valid RdDailyStockSummaryPageReqVO reqVO) {
        return success(dailyStockSummaryService.getTrend(reqVO));
    }

    @PostMapping("/rebuild")
    @Operation(summary = "按日期范围重算每日库存汇总")
    @PreAuthorize("@ss.hasPermission('rd:daily-stock-summary:rebuild')")
    public CommonResult<Integer> rebuild(
            @RequestParam("startDate") @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY) LocalDate endDate) {
        return success(dailyStockSummaryService.rebuild(startDate, endDate));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出每日库存汇总 Excel")
    @PreAuthorize("@ss.hasPermission('rd:daily-stock-summary:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportExcel(@Valid RdDailyStockSummaryPageReqVO pageReqVO,
                            HttpServletResponse response) throws IOException {
        pageReqVO.setPageSize(PageParam.PAGE_SIZE_NONE);
        List<RdDailyStockSummaryRespVO> list = BeanUtils.toBean(
                dailyStockSummaryService.getPage(pageReqVO).getList(), RdDailyStockSummaryRespVO.class);
        ExcelUtils.write(response, "每日出入库统计.xls", "数据", RdDailyStockSummaryRespVO.class, list);
    }

}
