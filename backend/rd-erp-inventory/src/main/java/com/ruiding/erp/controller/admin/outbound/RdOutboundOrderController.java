package com.ruiding.erp.controller.admin.outbound;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import com.ruiding.erp.controller.admin.outbound.vo.RdOutboundOrderPageReqVO;
import com.ruiding.erp.controller.admin.outbound.vo.RdOutboundOrderRespVO;
import com.ruiding.erp.controller.admin.outbound.vo.RdOutboundOrderSaveReqVO;
import com.ruiding.erp.controller.admin.outbound.vo.RdOutboundActionReqVO;
import com.ruiding.erp.controller.admin.outbound.vo.RdOutboundOrderDetailRespVO;
import com.ruiding.erp.controller.admin.outbound.vo.RdOutboundReverseReqVO;
import com.ruiding.erp.controller.admin.outbound.vo.RdOutboundScanReqVO;
import com.ruiding.erp.dal.dataobject.outbound.RdOutboundOrderLineDO;
import com.ruiding.erp.dal.dataobject.order.RdSaleOrderDO;
import com.ruiding.erp.dal.dataobject.outbound.RdOutboundOrderDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockLotDO;
import com.ruiding.erp.service.order.RdSaleOrderService;
import com.ruiding.erp.service.outbound.RdOutboundOrderService;
import com.ruiding.erp.service.stock.RdStockLotService;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.EXPORT;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserNickname;

@Tag(name = "管理后台 - RD 出库单")
@RestController
@RequestMapping("/rd/outbound-order")
@Validated
public class RdOutboundOrderController {

    @Resource
    private RdOutboundOrderService outboundOrderService;
    @Resource
    private RdSaleOrderService saleOrderService;
    @Resource
    private RdStockLotService stockLotService;

    @PostMapping("/create")
    @Operation(summary = "创建出库单")
    @PreAuthorize("@ss.hasPermission('rd:outbound-order:create')")
    public CommonResult<Long> createOutboundOrder(@Valid @RequestBody RdOutboundOrderSaveReqVO createReqVO) {
        return success(outboundOrderService.createOutboundOrder(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改出库单")
    @PreAuthorize("@ss.hasPermission('rd:outbound-order:update')")
    public CommonResult<Boolean> updateOutboundOrder(@Valid @RequestBody RdOutboundOrderSaveReqVO updateReqVO) {
        outboundOrderService.updateOutboundOrder(updateReqVO);
        return success(true);
    }

    @PutMapping("/confirm")
    @Operation(summary = "确认出库单")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('rd:outbound-scan:post')")
    public CommonResult<Boolean> confirmOutboundOrder(@RequestParam("id") Long id) {
        outboundOrderService.postOutboundOrder(id, null, getLoginUserId(), getLoginUserNickname());
        return success(true);
    }

    @PostMapping("/scan")
    @Operation(summary = "扫描出库包码")
    @PreAuthorize("@ss.hasPermission('rd:outbound-scan:scan')")
    public CommonResult<RdOutboundOrderLineDO> scanPackage(@Valid @RequestBody RdOutboundScanReqVO reqVO) {
        return success(outboundOrderService.scanPackage(reqVO, getLoginUserId(), getLoginUserNickname()));
    }

    @PutMapping("/remove-scan")
    @Operation(summary = "移除已扫描包码")
    @PreAuthorize("@ss.hasPermission('rd:outbound-scan:scan')")
    public CommonResult<Boolean> removeScannedPackage(@RequestParam("id") Long id,
                                                       @RequestParam("packageId") Long packageId) {
        outboundOrderService.removeScannedPackage(id, packageId);
        return success(true);
    }

    @PostMapping("/submit")
    @Operation(summary = "提交出库单")
    @PreAuthorize("@ss.hasPermission('rd:outbound-scan:submit')")
    public CommonResult<Boolean> submit(@Valid @RequestBody RdOutboundActionReqVO reqVO) {
        outboundOrderService.submitOutboundOrder(reqVO.getId(), reqVO.getVersion(),
                getLoginUserId(), getLoginUserNickname());
        return success(true);
    }

    @PostMapping("/approve")
    @Operation(summary = "审核出库单")
    @PreAuthorize("@ss.hasPermission('rd:outbound-scan:approve')")
    public CommonResult<Boolean> approve(@Valid @RequestBody RdOutboundActionReqVO reqVO) {
        outboundOrderService.approveOutboundOrder(reqVO.getId(), reqVO.getVersion(),
                getLoginUserId(), getLoginUserNickname());
        return success(true);
    }

    @PostMapping("/post")
    @Operation(summary = "过账出库单")
    @PreAuthorize("@ss.hasPermission('rd:outbound-scan:post')")
    public CommonResult<Boolean> post(@Valid @RequestBody RdOutboundActionReqVO reqVO) {
        outboundOrderService.postOutboundOrder(reqVO.getId(), reqVO.getVersion(),
                getLoginUserId(), getLoginUserNickname());
        return success(true);
    }

    @PostMapping("/reverse")
    @Operation(summary = "反向撤销已过账出库单")
    @PreAuthorize("@ss.hasPermission('rd:outbound-scan:reverse')")
    public CommonResult<Boolean> reverse(@Valid @RequestBody RdOutboundReverseReqVO reqVO) {
        outboundOrderService.reverseOutboundOrder(reqVO.getId(), reqVO.getVersion(), getLoginUserId(),
                getLoginUserNickname(), reqVO.getReason());
        return success(true);
    }

    @GetMapping("/detail")
    @Operation(summary = "获得出库单及扫描明细")
    @PreAuthorize("@ss.hasPermission('rd:outbound-order:query')")
    public CommonResult<RdOutboundOrderDetailRespVO> getDetail(@RequestParam("id") Long id) {
        return success(new RdOutboundOrderDetailRespVO(
                buildOutboundOrderRespVO(outboundOrderService.getOutboundOrder(id)),
                outboundOrderService.getOutboundOrderLines(id)));
    }

    @PutMapping("/cancel")
    @Operation(summary = "撤销出库单")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('rd:outbound-order:cancel')")
    public CommonResult<Boolean> cancelOutboundOrder(@RequestParam("id") Long id) {
        outboundOrderService.cancelOutboundOrder(id, getLoginUserId(), getLoginUserNickname());
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得出库单")
    @PreAuthorize("@ss.hasPermission('rd:outbound-order:query')")
    public CommonResult<RdOutboundOrderRespVO> getOutboundOrder(@RequestParam("id") Long id) {
        return success(buildOutboundOrderRespVO(outboundOrderService.getOutboundOrder(id)));
    }

    @GetMapping("/page")
    @Operation(summary = "获得出库单分页")
    @PreAuthorize("@ss.hasPermission('rd:outbound-order:query')")
    public CommonResult<PageResult<RdOutboundOrderRespVO>> getOutboundOrderPage(
            @Valid RdOutboundOrderPageReqVO pageReqVO) {
        return success(buildOutboundOrderPageRespVO(outboundOrderService.getOutboundOrderPage(pageReqVO)));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出出库单 Excel")
    @PreAuthorize("@ss.hasPermission('rd:outbound-order:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportOutboundOrderExcel(@Valid RdOutboundOrderPageReqVO pageReqVO,
                                         HttpServletResponse response) throws IOException {
        pageReqVO.setPageSize(PageParam.PAGE_SIZE_NONE);
        List<RdOutboundOrderRespVO> list = buildOutboundOrderPageRespVO(
                outboundOrderService.getOutboundOrderPage(pageReqVO)).getList();
        ExcelUtils.write(response, "出库单.xls", "数据", RdOutboundOrderRespVO.class, list);
    }

    private RdOutboundOrderRespVO buildOutboundOrderRespVO(RdOutboundOrderDO outboundOrder) {
        Map<Long, RdSaleOrderDO> orderMap = saleOrderService.getSaleOrderMap(
                Collections.singleton(outboundOrder.getOrderId()));
        Map<Long, RdStockLotDO> stockLotMap = stockLotService.getStockLotMap(
                Collections.singleton(outboundOrder.getStockLotId()));
        return fillOutboundOrderRespVO(BeanUtils.toBean(outboundOrder, RdOutboundOrderRespVO.class),
                orderMap, stockLotMap);
    }

    private PageResult<RdOutboundOrderRespVO> buildOutboundOrderPageRespVO(
            PageResult<RdOutboundOrderDO> pageResult) {
        Set<Long> orderIds = new HashSet<>();
        Set<Long> stockLotIds = new HashSet<>();
        for (RdOutboundOrderDO outboundOrder : pageResult.getList()) {
            orderIds.add(outboundOrder.getOrderId());
            stockLotIds.add(outboundOrder.getStockLotId());
        }
        Map<Long, RdSaleOrderDO> orderMap = saleOrderService.getSaleOrderMap(orderIds);
        Map<Long, RdStockLotDO> stockLotMap = stockLotService.getStockLotMap(stockLotIds);
        return BeanUtils.toBean(pageResult, RdOutboundOrderRespVO.class,
                outboundOrder -> fillOutboundOrderRespVO(outboundOrder, orderMap, stockLotMap));
    }

    private static RdOutboundOrderRespVO fillOutboundOrderRespVO(RdOutboundOrderRespVO outboundOrder,
                                                                 Map<Long, RdSaleOrderDO> orderMap,
                                                                 Map<Long, RdStockLotDO> stockLotMap) {
        RdSaleOrderDO saleOrder = orderMap.get(outboundOrder.getOrderId());
        RdStockLotDO stockLot = stockLotMap.get(outboundOrder.getStockLotId());
        if (saleOrder != null) {
            outboundOrder.setOrderNo(saleOrder.getOrderNo());
            outboundOrder.setCustomerName(saleOrder.getCustomerName());
            outboundOrder.setProductModel(saleOrder.getProductModel());
        }
        if (stockLot != null) {
            outboundOrder.setLocationCode(stockLot.getLocationCode());
            outboundOrder.setFurnaceNo(stockLot.getFurnaceNo());
            outboundOrder.setFurnaceLayer(stockLot.getFurnaceLayer());
            if (outboundOrder.getCustomerName() == null) {
                outboundOrder.setCustomerName(stockLot.getCustomerName());
            }
            if (outboundOrder.getProductModel() == null) {
                outboundOrder.setProductModel(stockLot.getProductModel());
            }
        }
        return outboundOrder;
    }

}
