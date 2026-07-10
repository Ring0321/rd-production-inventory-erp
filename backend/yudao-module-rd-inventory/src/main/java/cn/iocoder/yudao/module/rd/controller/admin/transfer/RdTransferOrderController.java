package cn.iocoder.yudao.module.rd.controller.admin.transfer;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.module.rd.controller.admin.transfer.vo.RdTransferOrderPageReqVO;
import cn.iocoder.yudao.module.rd.controller.admin.transfer.vo.RdTransferOrderRespVO;
import cn.iocoder.yudao.module.rd.controller.admin.transfer.vo.RdTransferOrderSaveReqVO;
import cn.iocoder.yudao.module.rd.dal.dataobject.order.RdSaleOrderDO;
import cn.iocoder.yudao.module.rd.dal.dataobject.stock.RdStockLotDO;
import cn.iocoder.yudao.module.rd.dal.dataobject.transfer.RdTransferOrderDO;
import cn.iocoder.yudao.module.rd.service.order.RdSaleOrderService;
import cn.iocoder.yudao.module.rd.service.stock.RdStockLotService;
import cn.iocoder.yudao.module.rd.service.transfer.RdTransferOrderService;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.EXPORT;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserNickname;

@Tag(name = "管理后台 - RD 调拨单")
@RestController
@RequestMapping("/rd/transfer-order")
@Validated
public class RdTransferOrderController {

    @Resource
    private RdTransferOrderService transferOrderService;
    @Resource
    private RdSaleOrderService saleOrderService;
    @Resource
    private RdStockLotService stockLotService;

    @PostMapping("/create")
    @Operation(summary = "创建调拨单")
    @PreAuthorize("@ss.hasPermission('rd:transfer-order:create')")
    public CommonResult<Long> createTransferOrder(@Valid @RequestBody RdTransferOrderSaveReqVO createReqVO) {
        return success(transferOrderService.createTransferOrder(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改调拨单")
    @PreAuthorize("@ss.hasPermission('rd:transfer-order:update')")
    public CommonResult<Boolean> updateTransferOrder(@Valid @RequestBody RdTransferOrderSaveReqVO updateReqVO) {
        transferOrderService.updateTransferOrder(updateReqVO);
        return success(true);
    }

    @PutMapping("/confirm")
    @Operation(summary = "确认调拨单")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('rd:transfer-order:confirm')")
    public CommonResult<Boolean> confirmTransferOrder(@RequestParam("id") Long id) {
        transferOrderService.confirmTransferOrder(id, getLoginUserId(), getLoginUserNickname());
        return success(true);
    }

    @PutMapping("/cancel")
    @Operation(summary = "撤销调拨单")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('rd:transfer-order:cancel')")
    public CommonResult<Boolean> cancelTransferOrder(@RequestParam("id") Long id) {
        transferOrderService.cancelTransferOrder(id, getLoginUserId(), getLoginUserNickname());
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得调拨单")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('rd:transfer-order:query')")
    public CommonResult<RdTransferOrderRespVO> getTransferOrder(@RequestParam("id") Long id) {
        RdTransferOrderDO transferOrder = transferOrderService.getTransferOrder(id);
        return success(buildTransferOrderRespVO(transferOrder));
    }

    @GetMapping("/page")
    @Operation(summary = "获得调拨单分页")
    @PreAuthorize("@ss.hasPermission('rd:transfer-order:query')")
    public CommonResult<PageResult<RdTransferOrderRespVO>> getTransferOrderPage(
            @Valid RdTransferOrderPageReqVO pageReqVO) {
        return success(buildTransferOrderPageRespVO(transferOrderService.getTransferOrderPage(pageReqVO)));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出调拨单 Excel")
    @PreAuthorize("@ss.hasPermission('rd:transfer-order:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportTransferOrderExcel(@Valid RdTransferOrderPageReqVO pageReqVO,
                                         HttpServletResponse response) throws IOException {
        pageReqVO.setPageSize(PageParam.PAGE_SIZE_NONE);
        List<RdTransferOrderRespVO> list = buildTransferOrderPageRespVO(
                transferOrderService.getTransferOrderPage(pageReqVO)).getList();
        ExcelUtils.write(response, "调拨单.xls", "数据", RdTransferOrderRespVO.class, list);
    }

    private RdTransferOrderRespVO buildTransferOrderRespVO(RdTransferOrderDO transferOrder) {
        Set<Long> orderIds = new HashSet<>();
        addIfNotNull(orderIds, transferOrder.getFromOrderId());
        addIfNotNull(orderIds, transferOrder.getToOrderId());
        Set<Long> stockLotIds = new HashSet<>();
        addIfNotNull(stockLotIds, transferOrder.getFromStockLotId());
        addIfNotNull(stockLotIds, transferOrder.getToStockLotId());
        RdTransferOrderRespVO respVO = BeanUtils.toBean(transferOrder, RdTransferOrderRespVO.class);
        fillTransferOrderRespVO(respVO, saleOrderService.getSaleOrderMap(orderIds),
                stockLotService.getStockLotMap(stockLotIds));
        return respVO;
    }

    private PageResult<RdTransferOrderRespVO> buildTransferOrderPageRespVO(
            PageResult<RdTransferOrderDO> pageResult) {
        Set<Long> orderIds = new HashSet<>();
        Set<Long> stockLotIds = new HashSet<>();
        for (RdTransferOrderDO transferOrder : pageResult.getList()) {
            addIfNotNull(orderIds, transferOrder.getFromOrderId());
            addIfNotNull(orderIds, transferOrder.getToOrderId());
            addIfNotNull(stockLotIds, transferOrder.getFromStockLotId());
            addIfNotNull(stockLotIds, transferOrder.getToStockLotId());
        }
        Map<Long, RdSaleOrderDO> orderMap = saleOrderService.getSaleOrderMap(orderIds);
        Map<Long, RdStockLotDO> stockLotMap = stockLotService.getStockLotMap(stockLotIds);
        return BeanUtils.toBean(pageResult, RdTransferOrderRespVO.class,
                transferOrder -> fillTransferOrderRespVO(transferOrder, orderMap, stockLotMap));
    }

    private static void fillTransferOrderRespVO(RdTransferOrderRespVO transferOrder,
                                                Map<Long, RdSaleOrderDO> orderMap,
                                                Map<Long, RdStockLotDO> stockLotMap) {
        RdSaleOrderDO fromOrder = orderMap.get(transferOrder.getFromOrderId());
        RdSaleOrderDO toOrder = orderMap.get(transferOrder.getToOrderId());
        RdStockLotDO fromStockLot = stockLotMap.get(transferOrder.getFromStockLotId());
        RdStockLotDO toStockLot = stockLotMap.get(transferOrder.getToStockLotId());
        if (fromOrder != null) {
            transferOrder.setFromOrderNo(fromOrder.getOrderNo());
        }
        if (toOrder != null) {
            transferOrder.setToOrderNo(toOrder.getOrderNo());
            transferOrder.setToCustomerName(toOrder.getCustomerName());
            transferOrder.setProductModel(toOrder.getProductModel());
        }
        if (fromStockLot != null) {
            transferOrder.setFromCustomerName(fromStockLot.getCustomerName());
            transferOrder.setFromLocationCode(fromStockLot.getLocationCode());
            transferOrder.setFurnaceNo(fromStockLot.getFurnaceNo());
            transferOrder.setFurnaceLayer(fromStockLot.getFurnaceLayer());
            if (transferOrder.getProductModel() == null) {
                transferOrder.setProductModel(fromStockLot.getProductModel());
            }
        }
        if (toStockLot != null) {
            transferOrder.setToLocationCode(toStockLot.getLocationCode());
        }
    }

    private static void addIfNotNull(Set<Long> ids, Long id) {
        if (id != null) {
            ids.add(id);
        }
    }

}
