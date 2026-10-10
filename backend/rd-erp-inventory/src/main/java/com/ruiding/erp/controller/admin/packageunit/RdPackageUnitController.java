package com.ruiding.erp.controller.admin.packageunit;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import com.ruiding.erp.controller.admin.packageunit.vo.RdPackageGenerateReqVO;
import com.ruiding.erp.controller.admin.packageunit.vo.RdPackageUnitExportRespVO;
import com.ruiding.erp.controller.admin.packageunit.vo.RdPackagePrintReqVO;
import com.ruiding.erp.controller.admin.packageunit.vo.RdPackageBatchPrintReqVO;
import com.ruiding.erp.controller.admin.packageunit.vo.RdPackageTraceRespVO;
import com.ruiding.erp.controller.admin.packageunit.vo.RdPackageWorkContextRespVO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.service.packageunit.RdPackageUnitService;
import com.ruiding.erp.service.packageunit.RdPackageWorkContextService;
import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import javax.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.io.IOException;
import java.util.Locale;
import javax.servlet.http.HttpServletResponse;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.EXPORT;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserNickname;
@RestController @RequestMapping("/rd/package-unit")
public class RdPackageUnitController {
    @Resource private RdPackageUnitService service;
    @Resource private RdPackageWorkContextService workContextService;
    @PostMapping("/generate") @PreAuthorize("@ss.hasPermission('rd:package-unit:generate')")
    public CommonResult<List<RdPackageUnitDO>> generate(@Valid @RequestBody RdPackageGenerateReqVO reqVO) {
        return success(service.generate(reqVO.getProductionOutputId(), reqVO.getPackageCount()));
    }
    @GetMapping("/get") @PreAuthorize("@ss.hasPermission('rd:package-unit:query')")
    public CommonResult<RdPackageUnitDO> get(@RequestParam Long id) { return success(service.get(id)); }
    @GetMapping("/page") @PreAuthorize("@ss.hasPermission('rd:package-unit:query')")
    public CommonResult<PageResult<RdPackageUnitDO>> page(@Valid PageParam pageParam) { return success(service.page(pageParam)); }
    @GetMapping("/trace") @PreAuthorize("@ss.hasPermission('rd:package-unit:query')")
    public CommonResult<List<RdPackageUnitDO>> trace(
            @RequestParam(required = false) String packageCode,
            @RequestParam(required = false) Long productionOutputId) {
        return success(service.trace(packageCode, productionOutputId));
    }
    @GetMapping("/export-excel") @PreAuthorize("@ss.hasPermission('rd:package-unit:query')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportExcel(@RequestParam(required = false) String packageCode,
                            @RequestParam(required = false) Long productionOutputId,
                            HttpServletResponse response) throws IOException {
        PageParam pageParam = new PageParam();
        pageParam.setPageSize(PageParam.PAGE_SIZE_NONE);
        String code = packageCode == null ? "" : packageCode.trim().toLowerCase(Locale.ROOT);
        List<RdPackageUnitExportRespVO> rows = BeanUtils.toBean(service.page(pageParam).getList().stream()
                .filter(unit -> code.isEmpty() || unit.getPackageCode().toLowerCase(Locale.ROOT).contains(code))
                .filter(unit -> productionOutputId == null || productionOutputId.equals(unit.getProductionOutputId()))
                .toList(), RdPackageUnitExportRespVO.class);
        ExcelUtils.write(response, "吨袋包码台账.xlsx", "包码明细", RdPackageUnitExportRespVO.class, rows);
    }
    @GetMapping("/public-trace/{packageCode}") @PermitAll
    public CommonResult<RdPackageTraceRespVO> publicTrace(@PathVariable String packageCode) {
        return success(service.getTraceDetail(packageCode, false));
    }
    @GetMapping("/trace-detail/{packageCode}") @PreAuthorize("@ss.hasPermission('rd:package-unit:query')")
    public CommonResult<RdPackageTraceRespVO> traceDetail(@PathVariable String packageCode) {
        return success(service.getTraceDetail(packageCode, true));
    }
    @GetMapping("/work-context/{packageCode}")
    @PreAuthorize("@ss.hasAnyPermissions('rd:package-unit:query', 'rd:putaway-task:execute', 'rd:loading-task:execute', 'rd:pickup-slip:query')")
    public CommonResult<RdPackageWorkContextRespVO> workContext(@PathVariable String packageCode) {
        return success(workContextService.getContext(packageCode, getLoginUserId()));
    }
    @PostMapping("/print") @PreAuthorize("@ss.hasPermission('rd:label-print:print')")
    public CommonResult<Boolean> print(@Valid @RequestBody RdPackagePrintReqVO reqVO) {
        service.markPrinted(List.of(reqVO.getPackageId()), getLoginUserId(), getLoginUserNickname(), false, null);
        return success(true);
    }
    @PostMapping("/reprint") @PreAuthorize("@ss.hasPermission('rd:label-print:reprint')")
    public CommonResult<Boolean> reprint(@Valid @RequestBody RdPackagePrintReqVO reqVO) {
        service.markPrinted(List.of(reqVO.getPackageId()), getLoginUserId(), getLoginUserNickname(), true,
                reqVO.getReprintReason());
        return success(true);
    }
    @PostMapping("/print-batch") @PreAuthorize("@ss.hasPermission('rd:label-print:print')")
    public CommonResult<Boolean> printBatch(@Valid @RequestBody RdPackageBatchPrintReqVO reqVO) {
        service.markPrinted(reqVO.getPackageIds(), getLoginUserId(), getLoginUserNickname(), false, null);
        return success(true);
    }
    @PostMapping("/reprint-batch") @PreAuthorize("@ss.hasPermission('rd:label-print:reprint')")
    public CommonResult<Boolean> reprintBatch(@Valid @RequestBody RdPackageBatchPrintReqVO reqVO) {
        service.markPrinted(reqVO.getPackageIds(), getLoginUserId(), getLoginUserNickname(), true,
                reqVO.getReprintReason());
        return success(true);
    }
}
