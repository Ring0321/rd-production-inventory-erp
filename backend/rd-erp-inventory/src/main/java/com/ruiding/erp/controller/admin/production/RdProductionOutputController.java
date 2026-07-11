package com.ruiding.erp.controller.admin.production;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import com.ruiding.erp.controller.admin.production.vo.RdProductionOutputPageReqVO;
import com.ruiding.erp.controller.admin.production.vo.RdProductionOutputRespVO;
import com.ruiding.erp.controller.admin.production.vo.RdProductionOutputSaveReqVO;
import com.ruiding.erp.dal.dataobject.production.RdProductionOutputDO;
import com.ruiding.erp.service.production.RdProductionOutputService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.util.List;

import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.EXPORT;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - RD 生产炉层产量")
@RestController
@RequestMapping("/rd/production-output")
@Validated
public class RdProductionOutputController {

    @Resource
    private RdProductionOutputService productionOutputService;

    @PostMapping("/create")
    @Operation(summary = "创建生产炉层产量")
    @PreAuthorize("@ss.hasPermission('rd:production-output:create')")
    public CommonResult<Long> createProductionOutput(@Valid @RequestBody RdProductionOutputSaveReqVO createReqVO) {
        return success(productionOutputService.createProductionOutput(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新生产炉层产量")
    @PreAuthorize("@ss.hasPermission('rd:production-output:update')")
    public CommonResult<Boolean> updateProductionOutput(@Valid @RequestBody RdProductionOutputSaveReqVO updateReqVO) {
        productionOutputService.updateProductionOutput(updateReqVO);
        return success(true);
    }

    @PutMapping("/cancel")
    @Operation(summary = "作废生产炉层产量")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('rd:production-output:cancel')")
    public CommonResult<Boolean> cancelProductionOutput(@RequestParam("id") Long id) {
        productionOutputService.cancelProductionOutput(id);
        return success(true);
    }

    @PutMapping("/inbound")
    @Operation(summary = "生产炉层产量转入库")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('rd:production-output:inbound')")
    public CommonResult<Boolean> inboundProductionOutput(@RequestParam("id") Long id) {
        productionOutputService.inboundProductionOutput(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得生产炉层产量")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('rd:production-output:query')")
    public CommonResult<RdProductionOutputRespVO> getProductionOutput(@RequestParam("id") Long id) {
        RdProductionOutputDO productionOutput = productionOutputService.getProductionOutput(id);
        return success(BeanUtils.toBean(productionOutput, RdProductionOutputRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得生产炉层产量分页")
    @PreAuthorize("@ss.hasPermission('rd:production-output:query')")
    public CommonResult<PageResult<RdProductionOutputRespVO>> getProductionOutputPage(
            @Valid RdProductionOutputPageReqVO pageReqVO) {
        PageResult<RdProductionOutputDO> pageResult = productionOutputService.getProductionOutputPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, RdProductionOutputRespVO.class));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出生产炉层产量 Excel")
    @PreAuthorize("@ss.hasPermission('rd:production-output:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportProductionOutputExcel(@Valid RdProductionOutputPageReqVO pageReqVO,
                                            HttpServletResponse response) throws IOException {
        pageReqVO.setPageSize(PageParam.PAGE_SIZE_NONE);
        List<RdProductionOutputDO> list = productionOutputService.getProductionOutputPage(pageReqVO).getList();
        ExcelUtils.write(response, "生产炉层产量.xls", "数据", RdProductionOutputRespVO.class,
                BeanUtils.toBean(list, RdProductionOutputRespVO.class));
    }

}
