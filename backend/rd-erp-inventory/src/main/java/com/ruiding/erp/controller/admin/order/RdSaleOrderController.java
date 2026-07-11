package com.ruiding.erp.controller.admin.order;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import com.ruiding.erp.controller.admin.order.vo.RdSaleOrderPageReqVO;
import com.ruiding.erp.controller.admin.order.vo.RdSaleOrderRespVO;
import com.ruiding.erp.controller.admin.order.vo.RdSaleOrderSaveReqVO;
import com.ruiding.erp.dal.dataobject.order.RdSaleOrderDO;
import com.ruiding.erp.service.order.RdSaleOrderService;
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

@Tag(name = "管理后台 - RD 销售订单")
@RestController
@RequestMapping("/rd/sale-order")
@Validated
public class RdSaleOrderController {

    @Resource
    private RdSaleOrderService saleOrderService;

    @PostMapping("/create")
    @Operation(summary = "创建销售订单")
    @PreAuthorize("@ss.hasPermission('rd:sale-order:create')")
    public CommonResult<Long> createSaleOrder(@Valid @RequestBody RdSaleOrderSaveReqVO createReqVO) {
        return success(saleOrderService.createSaleOrder(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改销售订单")
    @PreAuthorize("@ss.hasPermission('rd:sale-order:update')")
    public CommonResult<Boolean> updateSaleOrder(@Valid @RequestBody RdSaleOrderSaveReqVO updateReqVO) {
        saleOrderService.updateSaleOrder(updateReqVO);
        return success(true);
    }

    @PutMapping("/cancel")
    @Operation(summary = "取消销售订单")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('rd:sale-order:cancel')")
    public CommonResult<Boolean> cancelSaleOrder(@RequestParam("id") Long id) {
        saleOrderService.cancelSaleOrder(id);
        return success(true);
    }

    @PutMapping("/recalculate")
    @Operation(summary = "重新计算销售订单差额")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('rd:sale-order:recalculate')")
    public CommonResult<Boolean> recalculateSaleOrder(@RequestParam("id") Long id) {
        saleOrderService.recalculateSaleOrder(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得销售订单")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('rd:sale-order:query')")
    public CommonResult<RdSaleOrderRespVO> getSaleOrder(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(saleOrderService.getSaleOrder(id), RdSaleOrderRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得销售订单分页")
    @PreAuthorize("@ss.hasPermission('rd:sale-order:query')")
    public CommonResult<PageResult<RdSaleOrderRespVO>> getSaleOrderPage(@Valid RdSaleOrderPageReqVO pageReqVO) {
        PageResult<RdSaleOrderDO> pageResult = saleOrderService.getSaleOrderPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, RdSaleOrderRespVO.class));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出销售订单 Excel")
    @PreAuthorize("@ss.hasPermission('rd:sale-order:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportSaleOrderExcel(@Valid RdSaleOrderPageReqVO pageReqVO,
                                     HttpServletResponse response) throws IOException {
        pageReqVO.setPageSize(PageParam.PAGE_SIZE_NONE);
        List<RdSaleOrderDO> list = saleOrderService.getSaleOrderPage(pageReqVO).getList();
        ExcelUtils.write(response, "销售订单.xls", "数据", RdSaleOrderRespVO.class,
                BeanUtils.toBean(list, RdSaleOrderRespVO.class));
    }

}
