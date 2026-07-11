package com.ruiding.erp.controller.admin.stock;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import com.ruiding.erp.controller.admin.stock.vo.RdStockTransactionPageReqVO;
import com.ruiding.erp.controller.admin.stock.vo.RdStockTransactionRespVO;
import com.ruiding.erp.dal.dataobject.stock.RdStockTransactionDO;
import com.ruiding.erp.service.stock.RdStockTransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
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

@Tag(name = "管理后台 - RD 库存流水")
@RestController
@RequestMapping("/rd/stock-transaction")
@Validated
public class RdStockTransactionController {

    @Resource
    private RdStockTransactionService stockTransactionService;

    @GetMapping("/get")
    @Operation(summary = "获得库存流水")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('rd:stock-transaction:query')")
    public CommonResult<RdStockTransactionRespVO> getStockTransaction(@RequestParam("id") Long id) {
        RdStockTransactionDO stockTransaction = stockTransactionService.getStockTransaction(id);
        return success(BeanUtils.toBean(stockTransaction, RdStockTransactionRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得库存流水分页")
    @PreAuthorize("@ss.hasPermission('rd:stock-transaction:query')")
    public CommonResult<PageResult<RdStockTransactionRespVO>> getStockTransactionPage(
            @Valid RdStockTransactionPageReqVO pageReqVO) {
        PageResult<RdStockTransactionDO> pageResult = stockTransactionService.getStockTransactionPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, RdStockTransactionRespVO.class));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出库存流水 Excel")
    @PreAuthorize("@ss.hasPermission('rd:stock-transaction:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportStockTransactionExcel(@Valid RdStockTransactionPageReqVO pageReqVO,
                                            HttpServletResponse response) throws IOException {
        pageReqVO.setPageSize(PageParam.PAGE_SIZE_NONE);
        List<RdStockTransactionDO> list = stockTransactionService.getStockTransactionPage(pageReqVO).getList();
        ExcelUtils.write(response, "库存流水.xls", "数据", RdStockTransactionRespVO.class,
                BeanUtils.toBean(list, RdStockTransactionRespVO.class));
    }

}
