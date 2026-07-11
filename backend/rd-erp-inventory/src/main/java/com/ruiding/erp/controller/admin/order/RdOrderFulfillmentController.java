package com.ruiding.erp.controller.admin.order;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import com.ruiding.erp.controller.admin.order.vo.RdOrderFulfillmentAllocateReqVO;
import com.ruiding.erp.controller.admin.order.vo.RdOrderFulfillmentPageReqVO;
import com.ruiding.erp.controller.admin.order.vo.RdOrderFulfillmentRespVO;
import com.ruiding.erp.controller.admin.order.vo.RdOrderStockCandidatePageReqVO;
import com.ruiding.erp.controller.admin.order.vo.RdOrderStockCandidateRespVO;
import com.ruiding.erp.dal.dataobject.order.RdOrderFulfillmentDO;
import com.ruiding.erp.dal.dataobject.order.RdSaleOrderDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockLotDO;
import com.ruiding.erp.service.order.RdOrderFulfillmentService;
import com.ruiding.erp.service.order.RdSaleOrderService;
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
import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;

@Tag(name = "管理后台 - RD 订单库存匹配")
@RestController
@RequestMapping("/rd/order-fulfillment")
@Validated
public class RdOrderFulfillmentController {

    @Resource
    private RdOrderFulfillmentService fulfillmentService;
    @Resource
    private RdSaleOrderService saleOrderService;
    @Resource
    private RdStockLotService stockLotService;

    @PostMapping("/allocate")
    @Operation(summary = "手动匹配订单库存")
    @PreAuthorize("@ss.hasPermission('rd:order-fulfillment:allocate')")
    public CommonResult<Long> allocateStock(@Valid @RequestBody RdOrderFulfillmentAllocateReqVO allocateReqVO) {
        return success(fulfillmentService.allocateStock(allocateReqVO));
    }

    @PostMapping("/auto-allocate")
    @Operation(summary = "自动匹配订单库存")
    @Parameter(name = "orderId", description = "销售订单编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('rd:order-fulfillment:auto')")
    public CommonResult<BigDecimal> autoAllocateStock(@RequestParam("orderId") Long orderId) {
        return success(fulfillmentService.autoAllocateStock(orderId));
    }

    @PutMapping("/cancel")
    @Operation(summary = "取消订单库存匹配")
    @Parameter(name = "id", description = "匹配编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('rd:order-fulfillment:cancel')")
    public CommonResult<Boolean> cancelAllocation(@RequestParam("id") Long id) {
        fulfillmentService.cancelAllocation(id);
        return success(true);
    }

    @GetMapping("/candidate-page")
    @Operation(summary = "获得订单可匹配库存分页")
    @PreAuthorize("@ss.hasPermission('rd:order-fulfillment:query')")
    public CommonResult<PageResult<RdOrderStockCandidateRespVO>> getCandidateStockLotPage(
            @Valid RdOrderStockCandidatePageReqVO pageReqVO) {
        RdSaleOrderDO saleOrder = saleOrderService.getSaleOrder(pageReqVO.getOrderId());
        PageResult<RdStockLotDO> pageResult = fulfillmentService.getCandidateStockLotPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, RdOrderStockCandidateRespVO.class, stockLot -> {
            BigDecimal lockedQty = stockLot.getLockedQty() == null ? BigDecimal.ZERO : stockLot.getLockedQty();
            stockLot.setLockedQty(lockedQty);
            stockLot.setAvailableQty(stockLot.getWarehouseQty().subtract(lockedQty));
            stockLot.setSameCustomer(saleOrder.getCustomerName().equals(stockLot.getCustomerName()));
        }));
    }

    @GetMapping("/page")
    @Operation(summary = "获得订单库存匹配分页")
    @PreAuthorize("@ss.hasPermission('rd:order-fulfillment:query')")
    public CommonResult<PageResult<RdOrderFulfillmentRespVO>> getFulfillmentPage(
            @Valid RdOrderFulfillmentPageReqVO pageReqVO) {
        PageResult<RdOrderFulfillmentDO> pageResult = fulfillmentService.getFulfillmentPage(pageReqVO);
        Map<Long, RdStockLotDO> stockLotMap = stockLotService.getStockLotMap(
                convertSet(pageResult.getList(), RdOrderFulfillmentDO::getStockLotId));
        return success(BeanUtils.toBean(pageResult, RdOrderFulfillmentRespVO.class, fulfillment -> {
            RdStockLotDO stockLot = stockLotMap.get(fulfillment.getStockLotId());
            if (stockLot != null) {
                fulfillment.setLocationCode(stockLot.getLocationCode());
                fulfillment.setCustomerName(stockLot.getCustomerName());
                fulfillment.setProductModel(stockLot.getProductModel());
                fulfillment.setFurnaceNo(stockLot.getFurnaceNo());
                fulfillment.setFurnaceLayer(stockLot.getFurnaceLayer());
            }
        }));
    }

}
