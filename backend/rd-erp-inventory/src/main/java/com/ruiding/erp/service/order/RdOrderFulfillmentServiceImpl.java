package com.ruiding.erp.service.order;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.ruiding.erp.controller.admin.order.vo.RdOrderFulfillmentAllocateReqVO;
import com.ruiding.erp.controller.admin.order.vo.RdOrderFulfillmentPageReqVO;
import com.ruiding.erp.controller.admin.order.vo.RdOrderStockCandidatePageReqVO;
import com.ruiding.erp.dal.dataobject.order.RdOrderFulfillmentDO;
import com.ruiding.erp.dal.dataobject.order.RdSaleOrderDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockLotDO;
import com.ruiding.erp.dal.mysql.order.RdOrderFulfillmentMapper;
import com.ruiding.erp.dal.mysql.stock.RdStockLotMapper;
import com.ruiding.erp.enums.RdLogRecordConstants;
import com.ruiding.erp.enums.RdOrderFulfillmentStatusEnum;
import com.ruiding.erp.enums.RdStockLotStatusEnum;
import com.ruiding.erp.service.masterdata.RdWarehouseLocationFreezeGuard;
import com.mzt.logapi.context.LogRecordContext;
import com.mzt.logapi.starter.annotation.LogRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.math.BigDecimal;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.ruiding.erp.enums.ErrorCodeConstants.ORDER_FULFILLMENT_DUPLICATE;
import static com.ruiding.erp.enums.ErrorCodeConstants.ORDER_FULFILLMENT_NOT_EXISTS;
import static com.ruiding.erp.enums.ErrorCodeConstants.ORDER_FULFILLMENT_STATUS_CANNOT_CANCEL;
import static com.ruiding.erp.enums.ErrorCodeConstants.ORDER_MATCH_STOCK_DIMENSION_MISMATCH;
import static com.ruiding.erp.enums.ErrorCodeConstants.ORDER_MATCH_STOCK_INSUFFICIENT;
import static com.ruiding.erp.enums.ErrorCodeConstants.STOCK_LOT_NOT_EXISTS;

/**
 * RD order fulfillment Service implementation.
 */
@Service
@Validated
public class RdOrderFulfillmentServiceImpl implements RdOrderFulfillmentService {

    private static final String SOURCE_TYPE_STOCK = "STOCK";
    private static final int AUTO_ALLOCATE_MAX_LOTS = 1000;

    @Resource
    private RdOrderFulfillmentMapper fulfillmentMapper;
    @Resource
    private RdStockLotMapper stockLotMapper;
    @Resource
    private RdSaleOrderService saleOrderService;
    @Resource
    private RdWarehouseLocationFreezeGuard locationFreezeGuard;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = RdLogRecordConstants.FULFILLMENT_TYPE,
            subType = RdLogRecordConstants.FULFILLMENT_ALLOCATE_SUB_TYPE,
            bizNo = RdLogRecordConstants.BIZ_ID_EXPRESSION,
            success = RdLogRecordConstants.FULFILLMENT_ALLOCATE_SUCCESS)
    public Long allocateStock(RdOrderFulfillmentAllocateReqVO allocateReqVO) {
        BigDecimal quantity = allocateReqVO.getQuantity();
        RdSaleOrderDO saleOrder = saleOrderService.changeAllocatedQty(allocateReqVO.getOrderId(), quantity);
        RdStockLotDO stockLot = stockLotMapper.selectByIdForUpdate(allocateReqVO.getStockLotId());
        locationFreezeGuard.requireMovable(stockLot == null ? null : stockLot.getLocationId());
        validateStockLot(saleOrder, stockLot, quantity);
        if (fulfillmentMapper.selectActive(saleOrder.getId(), stockLot.getId()) != null) {
            throw exception(ORDER_FULFILLMENT_DUPLICATE);
        }

        BigDecimal lockedQty = zeroIfNull(stockLot.getLockedQty()).add(quantity);
        stockLotMapper.updateById(new RdStockLotDO().setId(stockLot.getId()).setLockedQty(lockedQty));

        RdOrderFulfillmentDO fulfillment = new RdOrderFulfillmentDO()
                .setOrderId(saleOrder.getId())
                .setStockLotId(stockLot.getId())
                .setMatchedQty(quantity)
                .setAllocatedQty(quantity)
                .setTransferQty(BigDecimal.ZERO)
                .setOutboundQty(BigDecimal.ZERO)
                .setSourceType(SOURCE_TYPE_STOCK)
                .setStatus(RdOrderFulfillmentStatusEnum.ALLOCATED.getStatus())
                .setRemark(allocateReqVO.getRemark());
        fulfillmentMapper.insert(fulfillment);
        LogRecordContext.putVariable(RdLogRecordConstants.BIZ_ID_VARIABLE, fulfillment.getId());
        return fulfillment.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = RdLogRecordConstants.FULFILLMENT_TYPE,
            subType = RdLogRecordConstants.FULFILLMENT_AUTO_ALLOCATE_SUB_TYPE,
            bizNo = "{{#orderId}}", success = RdLogRecordConstants.FULFILLMENT_AUTO_ALLOCATE_SUCCESS)
    public BigDecimal autoAllocateStock(Long orderId) {
        RdSaleOrderDO saleOrder = saleOrderService.getSaleOrder(orderId);
        BigDecimal remainingQty = saleOrder.getAdjustedDifferenceQty().max(BigDecimal.ZERO);
        if (remainingQty.signum() == 0) {
            return BigDecimal.ZERO;
        }

        RdOrderStockCandidatePageReqVO candidateReqVO = new RdOrderStockCandidatePageReqVO();
        candidateReqVO.setOrderId(orderId);
        candidateReqVO.setPageNo(1);
        candidateReqVO.setPageSize(AUTO_ALLOCATE_MAX_LOTS);
        PageResult<RdStockLotDO> candidatePage = getCandidateStockLotPage(candidateReqVO);
        BigDecimal allocatedTotal = BigDecimal.ZERO;
        for (RdStockLotDO stockLot : candidatePage.getList()) {
            BigDecimal availableQty = stockLot.getWarehouseQty().subtract(zeroIfNull(stockLot.getLockedQty()));
            BigDecimal allocateQty = remainingQty.min(availableQty);
            if (allocateQty.signum() <= 0) {
                continue;
            }
            RdOrderFulfillmentAllocateReqVO allocateReqVO = new RdOrderFulfillmentAllocateReqVO();
            allocateReqVO.setOrderId(orderId);
            allocateReqVO.setStockLotId(stockLot.getId());
            allocateReqVO.setQuantity(allocateQty);
            allocateReqVO.setRemark("自动匹配库存");
            allocateStock(allocateReqVO);
            allocatedTotal = allocatedTotal.add(allocateQty);
            remainingQty = remainingQty.subtract(allocateQty);
            if (remainingQty.signum() == 0) {
                break;
            }
        }
        return allocatedTotal;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = RdLogRecordConstants.FULFILLMENT_TYPE,
            subType = RdLogRecordConstants.FULFILLMENT_CANCEL_SUB_TYPE,
            bizNo = "{{#id}}", success = RdLogRecordConstants.FULFILLMENT_CANCEL_SUCCESS)
    public void cancelAllocation(Long id) {
        RdOrderFulfillmentDO currentFulfillment = fulfillmentMapper.selectById(id);
        if (currentFulfillment == null) {
            throw exception(ORDER_FULFILLMENT_NOT_EXISTS);
        }
        saleOrderService.changeAllocatedQty(
                currentFulfillment.getOrderId(), currentFulfillment.getAllocatedQty().negate());

        RdOrderFulfillmentDO fulfillment = fulfillmentMapper.selectByIdForUpdate(id);
        if (!RdOrderFulfillmentStatusEnum.ALLOCATED.getStatus().equals(fulfillment.getStatus())
                || zeroIfNull(fulfillment.getOutboundQty()).signum() > 0) {
            throw exception(ORDER_FULFILLMENT_STATUS_CANNOT_CANCEL);
        }
        RdStockLotDO stockLot = stockLotMapper.selectByIdForUpdate(fulfillment.getStockLotId());
        locationFreezeGuard.requireMovable(stockLot == null ? null : stockLot.getLocationId());
        if (stockLot == null) {
            throw exception(STOCK_LOT_NOT_EXISTS);
        }
        BigDecimal lockedQty = zeroIfNull(stockLot.getLockedQty()).subtract(fulfillment.getAllocatedQty());
        if (lockedQty.signum() < 0) {
            throw exception(ORDER_MATCH_STOCK_INSUFFICIENT);
        }
        stockLotMapper.updateById(new RdStockLotDO().setId(stockLot.getId()).setLockedQty(lockedQty));
        fulfillmentMapper.updateById(new RdOrderFulfillmentDO()
                .setId(fulfillment.getId())
                .setStatus(RdOrderFulfillmentStatusEnum.CANCELLED.getStatus()));
    }

    @Override
    public PageResult<RdStockLotDO> getCandidateStockLotPage(RdOrderStockCandidatePageReqVO pageReqVO) {
        RdSaleOrderDO saleOrder = saleOrderService.getSaleOrder(pageReqVO.getOrderId());
        return stockLotMapper.selectMatchCandidatePage(pageReqVO, saleOrder);
    }

    @Override
    public PageResult<RdOrderFulfillmentDO> getFulfillmentPage(RdOrderFulfillmentPageReqVO pageReqVO) {
        return fulfillmentMapper.selectPage(pageReqVO);
    }

    private static void validateStockLot(RdSaleOrderDO saleOrder, RdStockLotDO stockLot, BigDecimal quantity) {
        if (stockLot == null) {
            throw exception(STOCK_LOT_NOT_EXISTS);
        }
        if (!saleOrder.getCustomerName().equals(stockLot.getCustomerName())
                || !saleOrder.getProductModel().equals(stockLot.getProductModel())) {
            throw exception(ORDER_MATCH_STOCK_DIMENSION_MISMATCH);
        }
        if (RdStockLotStatusEnum.CLOSED.getStatus().equals(stockLot.getStatus())
                || RdStockLotStatusEnum.EMPTY.getStatus().equals(stockLot.getStatus())) {
            throw exception(ORDER_MATCH_STOCK_INSUFFICIENT);
        }
        BigDecimal availableQty = stockLot.getWarehouseQty().subtract(zeroIfNull(stockLot.getLockedQty()));
        if (quantity.compareTo(availableQty) > 0) {
            throw exception(ORDER_MATCH_STOCK_INSUFFICIENT);
        }
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

}
