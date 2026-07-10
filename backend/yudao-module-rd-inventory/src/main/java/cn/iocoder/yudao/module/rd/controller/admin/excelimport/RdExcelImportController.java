package cn.iocoder.yudao.module.rd.controller.admin.excelimport;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.module.rd.controller.admin.excelimport.vo.RdExcelImportErrorPageReqVO;
import cn.iocoder.yudao.module.rd.controller.admin.excelimport.vo.RdExcelImportErrorRespVO;
import cn.iocoder.yudao.module.rd.controller.admin.excelimport.vo.RdExcelImportRowPageReqVO;
import cn.iocoder.yudao.module.rd.controller.admin.excelimport.vo.RdExcelImportRowRespVO;
import cn.iocoder.yudao.module.rd.controller.admin.excelimport.vo.RdExcelImportTaskPageReqVO;
import cn.iocoder.yudao.module.rd.controller.admin.excelimport.vo.RdExcelImportTaskRespVO;
import cn.iocoder.yudao.module.rd.dal.dataobject.excelimport.RdExcelImportErrorDO;
import cn.iocoder.yudao.module.rd.service.excelimport.RdExcelImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.EXPORT;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserNickname;

@Tag(name = "管理后台 - RD 库存 Excel 导入")
@RestController
@RequestMapping("/rd/excel-import")
@Validated
public class RdExcelImportController {

    @Resource
    private RdExcelImportService importService;

    @PostMapping("/precheck")
    @Operation(summary = "上传库存 Excel 并执行预检")
    @PreAuthorize("@ss.hasPermission('rd:excel-import:create')")
    public CommonResult<Long> precheck(
            @RequestParam("file") MultipartFile file,
            @RequestParam("bizDate") @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY) LocalDate bizDate,
            @RequestParam(value = "sheetName", required = false) String sheetName) {
        return success(importService.precheck(file, bizDate, sheetName));
    }

    @PutMapping("/confirm")
    @Operation(summary = "确认导入当前库存")
    @PreAuthorize("@ss.hasPermission('rd:excel-import:confirm')")
    public CommonResult<Integer> confirm(@RequestParam("id") Long id) {
        return success(importService.confirm(id, getLoginUserId(), getLoginUserNickname()));
    }

    @GetMapping("/get")
    @Operation(summary = "获得 Excel 导入任务")
    @PreAuthorize("@ss.hasPermission('rd:excel-import:query')")
    public CommonResult<RdExcelImportTaskRespVO> get(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(importService.getTask(id), RdExcelImportTaskRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得 Excel 导入任务分页")
    @PreAuthorize("@ss.hasPermission('rd:excel-import:query')")
    public CommonResult<PageResult<RdExcelImportTaskRespVO>> getTaskPage(
            @Valid RdExcelImportTaskPageReqVO reqVO) {
        return success(BeanUtils.toBean(importService.getTaskPage(reqVO), RdExcelImportTaskRespVO.class));
    }

    @GetMapping("/row-page")
    @Operation(summary = "获得 Excel 导入行预览分页")
    @PreAuthorize("@ss.hasPermission('rd:excel-import:query')")
    public CommonResult<PageResult<RdExcelImportRowRespVO>> getRowPage(
            @Valid RdExcelImportRowPageReqVO reqVO) {
        return success(BeanUtils.toBean(importService.getRowPage(reqVO), RdExcelImportRowRespVO.class));
    }

    @GetMapping("/error-page")
    @Operation(summary = "获得 Excel 导入错误分页")
    @PreAuthorize("@ss.hasPermission('rd:excel-import:query')")
    public CommonResult<PageResult<RdExcelImportErrorRespVO>> getErrorPage(
            @Valid RdExcelImportErrorPageReqVO reqVO) {
        return success(BeanUtils.toBean(importService.getErrorPage(reqVO), RdExcelImportErrorRespVO.class));
    }

    @GetMapping("/export-errors")
    @Operation(summary = "导出 Excel 预检错误")
    @PreAuthorize("@ss.hasPermission('rd:excel-import:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportErrors(@Valid RdExcelImportErrorPageReqVO reqVO,
                             HttpServletResponse response) throws IOException {
        reqVO.setPageSize(PageParam.PAGE_SIZE_NONE);
        List<RdExcelImportErrorDO> errors = importService.getErrorPage(reqVO).getList();
        ExcelUtils.write(response, "库存导入错误.xls", "错误明细", RdExcelImportErrorRespVO.class,
                BeanUtils.toBean(errors, RdExcelImportErrorRespVO.class));
    }

}
