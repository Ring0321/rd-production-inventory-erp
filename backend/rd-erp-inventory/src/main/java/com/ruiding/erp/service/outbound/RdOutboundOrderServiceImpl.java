package com.ruiding.erp.service.outbound;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.ruiding.erp.controller.admin.outbound.vo.RdOutboundOrderPageReqVO;
import com.ruiding.erp.controller.admin.outbound.vo.RdOutboundOrderSaveReqVO;
import com.ruiding.erp.dal.dataobject.order.RdOrderFulfillmentDO;
import com.ruiding.erp.dal.dataobject.order.RdSaleOrderDO;
import com.ruiding.erp.dal.dataobject.outbound.RdOutboundOrderDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockLotDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockTransactionDO;
import com.ruiding.erp.dal.mysql.order.RdOrderFulfillmentMapper;
import com.ruiding.erp.dal.mysql.outbound.RdOutboundOrderMapper;
import com.ruiding.erp.dal.mysql.stock.RdStockLotMapper;
import com.ruiding.erp.enums.RdLogRecordConstants;
import com.ruiding.erp.enums.RdOrderFulfillmentStatusEnum;
import com.ruiding.erp.enums.RdOrderStatusEnum;
import com.ruiding.erp.enums.RdOutboundOrderStatusEnum;
import com.ruiding.erp.enums.RdStockLotStatusEnum;
import com.ruiding.erp.enums.RdStockTransactionTypeEnum;
import com.ruiding.erp.service.order.RdSaleOrderService;
import com.ruiding.erp.service.stock.RdStockTransactionService;
import com.mzt.logapi.context.LogRecordContext;
import com.mzt.logapi.starter.annotation.LogRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.ruiding.erp.enums.ErrorCodeConstants.ORDER_FULFILLMENT_NOT_EXISTS;
import static com.ruiding.erp.enums.ErrorCodeConstants.OUTBOUND_ORDER_DRAFT_EXISTS;
import static com.ruiding.erp.enums.ErrorCodeConstants.OUTBOUND_ORDER_FULFILLMENT_MISMATCH;
import static com.ruiding.erp.enums.ErrorCodeConstants.OUTBOUND_ORDER_NOT_EXISTS;
import static com.ruiding.erp.enums.ErrorCodeConstants.OUTBOUND_ORDER_NO_EXISTS;
import static com.ruiding.erp.enums.ErrorCodeConstants.OUTBOUND_ORDER_QUANTITY_EXCEEDS_ALLOCATION;
import static com.ruiding.erp.enums.ErrorCodeConstants.OUTBOUND_ORDER_REVERSE_INSUFFICIENT;
import static com.ruiding.erp.enums.ErrorCodeConstants.OUTBOUND_ORDER_STATUS_CANNOT_CANCEL;
import static com.ruiding.erp.enums.ErrorCodeConstants.OUTBOUND_ORDER_STATUS_NOT_DRAFT;
import static com.ruiding.erp.enums.ErrorCodeConstants.OUTBOUND_ORDER_STOCK_INSUFFICIENT;
import static com.ruiding.erp.enums.ErrorCodeConstants.SALE_ORDER_STATUS_CANNOT_CHANGE;
import static com.ruiding.erp.enums.ErrorCodeConstants.STOCK_LOT_NOT_EXISTS;

/**
 * RD outbound order Service implementation.
 */
@Service
@Validated
public class RdOutboundOrderServiceImpl implements RdOutboundOrderService {

    private static final String SOURCE_TYPE_OUTBOUND_ORDER = "OUTBOUND_ORDER";
    private static final String SOURCE_TYPE_OUTBOUND_REVERSAL = "OUTBOUND_REVERSAL";

    @Resource
    private RdOutboundOrderMapper outboundOrderMapper;
    @Resource
    private RdOrderFulfillmentMapper fulfillmentMapper;
    @Resource
    private RdStockLotMapper stockLotMapper;
    @Resource
    private RdSaleOrderService saleOrderService;
    @Resource
    private RdStockTransactionService stockTransactionService;

    @Override
    @LogRecord(type = RdLogRecordConstants.OUTBOUND_TYPE,
            subType = RdLogRecordConstants.OUTBOUND_CREATE_SUB_TYPE,
            bizNo = RdLogRecordConstants.BIZ_ID_EXPRESSION,
            success = RdLogRecordConstants.OUTBOUND_CREATE_SUCCESS)
    public Long createOutboundOrder(RdOutboundOrderSaveReqVO createReqVO) {
        validateOutboundNoUnique(null, createReqVO.getOutboundNo());
        validateDraftUnique(null, createReqVO.getFulfillmentId());
        RdOrderFulfillmentDO fulfillment = fulfillmentMapper.selectById(createReqVO.getFulfillmentId());
        RdStockLotDO stockLot = getStockLot(fulfillment);
        validateOutboundBusiness(createReqVO, fulfillment, stockLot);
        RdOutboundOrderDO outboundOrder = buildOutboundOrder(createReqVO, stockLot)
                .setStockLotId(stockLot.getId())
                .setStatus(RdOutboundOrderStatusEnum.DRAFT.getStatus());
        outboundOrderMapper.insert(outboundOrder);
        LogRecordContext.putVariable(RdLogRecordConstants.BIZ_ID_VARIABLE, outboundOrder.getId());
        return outboundOrder.getId();
    }

    @Override
    @LogRecord(type = RdLogRecordConstants.OUTBOUND_TYPE,
            subType = RdLogRecordConstants.OUTBOUND_UPDATE_SUB_TYPE,
            bizNo = "{{#updateReqVO.id}}", success = RdLogRecordConstants.OUTBOUND_UPDATE_SUCCESS)
    public void updateOutboundOrder(RdOutboundOrderSaveReqVO updateReqVO) {
        RdOutboundOrderDO outboundOrder = validateOutboundOrderDraft(updateReqVO.getId());
        validateOutboundNoUnique(updateReqVO.getId(), updateReqVO.getOutboundNo());
        validateDraftUnique(updateReqVO.getId(), updateReqVO.getFulfillmentId());
        RdOrderFulfillmentDO fulfillment = fulfillmentMapper.selectById(updateReqVO.getFulfillmentId());
        RdStockLotDO stockLot = getStockLot(fulfillment);
        validateOutboundBusiness(updateReqVO, fulfillment, stockLot);
        RdOutboundOrderDO updateObj = buildOutboundOrder(updateReqVO, stockLot)
                .setId(outboundOrder.getId())
                .setStockLotId(stockLot.getId());
        outboundOrderMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = RdLogRecordConstants.OUTBOUND_TYPE,
            subType = RdLogRecordConstants.OUTBOUND_CONFIRM_SUB_TYPE,
            bizNo = "{{#id}}", success = RdLogRecordConstants.OUTBOUND_CONFIRM_SUCCESS)
    public void confirmOutboundOrder(Long id, Long operatorId, String operatorName) {
        RdOutboundOrderDO outboundOrder = outboundOrderMapper.selectByIdForUpdate(id);
        validateConfirmStatus(outboundOrder);
        saleOrderService.lockSaleOrderForOutbound(outboundOrder.getOrderId(), false);
        RdOrderFulfillmentDO fulfillment = fulfillmentMapper.selectByIdForUpdate(outboundOrder.getFulfillmentId());
        RdStockLotDO stockLot = stockLotMapper.selectByIdForUpdate(outboundOrder.getStockLotId());
        validateOutboundBusiness(outboundOrder, fulfillment, stockLot);

        RdOutboundQuantityCalculator.Result result = calculateConfirm(outboundOrder, fulfillment, stockLot);
        BigDecimal beforeQty = stockLot.getWarehouseQty();
        applyStockResult(stockLot, result, outboundOrder.getOutboundDate());
        stockLotMapper.updateById(stockLot);
        fulfillmentMapper.updateById(new RdOrderFulfillmentDO()
                .setId(fulfillment.getId())
                .setOutboundQty(result.getFulfillmentOutboundQty())
                .setStatus(result.getRemainingAllocatedQty().signum() == 0
                        ? RdOrderFulfillmentStatusEnum.OUTBOUNDED.getStatus()
                        : RdOrderFulfillmentStatusEnum.ALLOCATED.getStatus()));
        createOutboundTransaction(outboundOrder, stockLot, outboundOrder.getQuantity().negate(),
                beforeQty, result.getWarehouseQty(), SOURCE_TYPE_OUTBOUND_ORDER, operatorId, operatorName);
        outboundOrderMapper.updateById(new RdOutboundOrderDO()
                .setId(id)
                .setStatus(RdOutboundOrderStatusEnum.CONFIRMED.getStatus()));
        saleOrderService.syncOutboundStatus(outboundOrder.getOrderId(),
                fulfillmentMapper.sumOutboundQtyByOrderId(outboundOrder.getOrderId()), operatorName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = RdLogRecordConstants.OUTBOUND_TYPE,
            subType = RdLogRecordConstants.OUTBOUND_CANCEL_SUB_TYPE,
            bizNo = "{{#id}}", success = RdLogRecordConstants.OUTBOUND_CANCEL_SUCCESS)
    public void cancelOutboundOrder(Long id, Long operatorId, String operatorName) {
        RdOutboundOrderDO outboundOrder = outboundOrderMapper.selectByIdForUpdate(id);
        if (outboundOrder == null) {
            throw exception(OUTBOUND_ORDER_NOT_EXISTS);
        }
        if (RdOutboundOrderStatusEnum.DRAFT.getStatus().equals(outboundOrder.getStatus())) {
            outboundOrderMapper.updateById(new RdOutboundOrderDO()
                    .setId(id)
                    .setStatus(RdOutboundOrderStatusEnum.CANCELLED.getStatus()));
            return;
        }
        if (!RdOutboundOrderStatusEnum.CONFIRMED.getStatus().equals(outboundOrder.getStatus())) {
            throw exception(OUTBOUND_ORDER_STATUS_CANNOT_CANCEL);
        }

        saleOrderService.lockSaleOrderForOutbound(outboundOrder.getOrderId(), true);
        RdOrderFulfillmentDO fulfillment = fulfillmentMapper.selectByIdForUpdate(outboundOrder.getFulfillmentId());
        RdStockLotDO stockLot = stockLotMapper.selectByIdForUpdate(outboundOrder.getStockLotId());
        validateOutboundReferences(outboundOrder, fulfillment, stockLot);
        RdOutboundQuantityCalculator.Result result = calculateCancel(outboundOrder, fulfillment, stockLot);
        LocalDate reverseDate = LocalDate.now();
        BigDecimal beforeQty = stockLot.getWarehouseQty();
        applyStockResult(stockLot, result, reverseDate);
        stockLotMapper.updateById(stockLot);
        fulfillmentMapper.updateById(new RdOrderFulfillmentDO()
                .setId(fulfillment.getId())
                .setOutboundQty(result.getFulfillmentOutboundQty())
                .setStatus(RdOrderFulfillmentStatusEnum.ALLOCATED.getStatus()));

        RdOutboundOrderDO reverseOrder = new RdOutboundOrderDO()
                .setId(outboundOrder.getId())
                .setOutboundNo(outboundOrder.getOutboundNo())
                .setOutboundDate(reverseDate)
                .setOrderId(outboundOrder.getOrderId())
                .setQuantity(outboundOrder.getQuantity());
        createOutboundTransaction(reverseOrder, stockLot, outboundOrder.getQuantity(),
                beforeQty, result.getWarehouseQty(), SOURCE_TYPE_OUTBOUND_REVERSAL, operatorId, operatorName);
        outboundOrderMapper.updateById(new RdOutboundOrderDO()
                .setId(id)
                .setStatus(RdOutboundOrderStatusEnum.CANCELLED.getStatus()));
        saleOrderService.syncOutboundStatus(outboundOrder.getOrderId(),
                fulfillmentMapper.sumOutboundQtyByOrderId(outboundOrder.getOrderId()), operatorName);
    }

    @Override
    public RdOutboundOrderDO getOutboundOrder(Long id) {
        RdOutboundOrderDO outboundOrder = outboundOrderMapper.selectById(id);
        if (outboundOrder == null) {
            throw exception(OUTBOUND_ORDER_NOT_EXISTS);
        }
        return outboundOrder;
    }

    @Override
    public PageResult<RdOutboundOrderDO> getOutboundOrderPage(RdOutboundOrderPageReqVO pageReqVO) {
        return outboundOrderMapper.selectPage(pageReqVO);
    }

    private void validateConfirmStatus(RdOutboundOrderDO outboundOrder) {
        if (outboundOrder == null) {
            throw exception(OUTBOUND_ORDER_NOT_EXISTS);
        }
        if (!RdOutboundOrderStatusEnum.DRAFT.getStatus().equals(outboundOrder.getStatus())) {
            throw exception(OUTBOUND_ORDER_STATUS_NOT_DRAFT);
        }
    }

    private RdOutboundOrderDO validateOutboundOrderDraft(Long id) {
        RdOutboundOrderDO outboundOrder = getOutboundOrder(id);
        if (!RdOutboundOrderStatusEnum.DRAFT.getStatus().equals(outboundOrder.getStatus())) {
            throw exception(OUTBOUND_ORDER_STATUS_NOT_DRAFT);
        }
        return outboundOrder;
    }

    private void validateOutboundNoUnique(Long id, String outboundNo) {
        RdOutboundOrderDO existing = outboundOrderMapper.selectByOutboundNo(outboundNo);
        if (existing != null && (id == null || !existing.getId().equals(id))) {
            throw exception(OUTBOUND_ORDER_NO_EXISTS);
        }
    }

    private void validateDraftUnique(Long id, Long fulfillmentId) {
        RdOutboundOrderDO existing = outboundOrderMapper.selectDraftByFulfillmentId(fulfillmentId);
        if (existing != null && (id == null || !existing.getId().equals(id))) {
            throw exception(OUTBOUND_ORDER_DRAFT_EXISTS);
        }
    }

    private RdStockLotDO getStockLot(RdOrderFulfillmentDO fulfillment) {
        if (fulfillment == null) {
            throw exception(ORDER_FULFILLMENT_NOT_EXISTS);
        }
        RdStockLotDO stockLot = stockLotMapper.selectById(fulfillment.getStockLotId());
        if (stockLot == null) {
            throw exception(STOCK_LOT_NOT_EXISTS);
        }
        return stockLot;
    }

    private void validateOutboundBusiness(RdOutboundOrderSaveReqVO reqVO,
                                          RdOrderFulfillmentDO fulfillment, RdStockLotDO stockLot) {
        RdSaleOrderDO saleOrder = saleOrderService.getSaleOrder(reqVO.getOrderId());
        if (RdOrderStatusEnum.CLOSED.getStatus().equals(saleOrder.getStatus())
                || RdOrderStatusEnum.CANCELLED.getStatus().equals(saleOrder.getStatus())) {
            throw exception(SALE_ORDER_STATUS_CANNOT_CHANGE);
        }
        validateOutboundReferences(reqVO.getOrderId(), reqVO.getFulfillmentId(),
                fulfillment == null ? null : fulfillment.getStockLotId(), fulfillment, stockLot);
        validateRemainingQuantity(fulfillment, reqVO.getQuantity());
    }

    private void validateOutboundBusiness(RdOutboundOrderDO outboundOrder,
                                          RdOrderFulfillmentDO fulfillment, RdStockLotDO stockLot) {
        validateOutboundReferences(outboundOrder, fulfillment, stockLot);
        validateRemainingQuantity(fulfillment, outboundOrder.getQuantity());
    }

    private static void validateOutboundReferences(RdOutboundOrderDO outboundOrder,
                                                   RdOrderFulfillmentDO fulfillment, RdStockLotDO stockLot) {
        validateOutboundReferences(outboundOrder.getOrderId(), outboundOrder.getFulfillmentId(),
                outboundOrder.getStockLotId(), fulfillment, stockLot);
    }

    private static void validateOutboundReferences(Long orderId, Long fulfillmentId, Long stockLotId,
                                                   RdOrderFulfillmentDO fulfillment, RdStockLotDO stockLot) {
        if (fulfillment == null) {
            throw exception(ORDER_FULFILLMENT_NOT_EXISTS);
        }
        if (stockLot == null) {
            throw exception(STOCK_LOT_NOT_EXISTS);
        }
        if (!fulfillment.getId().equals(fulfillmentId)
                || !fulfillment.getOrderId().equals(orderId)
                || !fulfillment.getStockLotId().equals(stockLotId)
                || !stockLot.getId().equals(stockLotId)
                || (!RdOrderFulfillmentStatusEnum.ALLOCATED.getStatus().equals(fulfillment.getStatus())
                && !RdOrderFulfillmentStatusEnum.OUTBOUNDED.getStatus().equals(fulfillment.getStatus()))) {
            throw exception(OUTBOUND_ORDER_FULFILLMENT_MISMATCH);
        }
    }

    private static void validateRemainingQuantity(RdOrderFulfillmentDO fulfillment, BigDecimal quantity) {
        BigDecimal remaining = fulfillment.getAllocatedQty().subtract(zeroIfNull(fulfillment.getOutboundQty()));
        if (remaining.compareTo(quantity) < 0 || quantity.signum() <= 0) {
            throw exception(OUTBOUND_ORDER_QUANTITY_EXCEEDS_ALLOCATION);
        }
    }

    private static RdOutboundQuantityCalculator.Result calculateConfirm(RdOutboundOrderDO outboundOrder,
                                                                        RdOrderFulfillmentDO fulfillment,
                                                                        RdStockLotDO stockLot) {
        if (isStockUnavailable(stockLot.getStatus())) {
            throw exception(OUTBOUND_ORDER_STOCK_INSUFFICIENT);
        }
        try {
            return RdOutboundQuantityCalculator.confirm(stockLot.getWarehouseQty(),
                    zeroIfNull(stockLot.getLockedQty()), zeroIfNull(stockLot.getOutboundQty()),
                    fulfillment.getAllocatedQty(), zeroIfNull(fulfillment.getOutboundQty()),
                    outboundOrder.getQuantity());
        } catch (IllegalArgumentException ex) {
            BigDecimal remaining = fulfillment.getAllocatedQty().subtract(zeroIfNull(fulfillment.getOutboundQty()));
            if (remaining.compareTo(outboundOrder.getQuantity()) < 0) {
                throw exception(OUTBOUND_ORDER_QUANTITY_EXCEEDS_ALLOCATION);
            }
            throw exception(OUTBOUND_ORDER_STOCK_INSUFFICIENT);
        }
    }

    private static RdOutboundQuantityCalculator.Result calculateCancel(RdOutboundOrderDO outboundOrder,
                                                                       RdOrderFulfillmentDO fulfillment,
                                                                       RdStockLotDO stockLot) {
        if (RdStockLotStatusEnum.CLOSED.getStatus().equals(stockLot.getStatus())) {
            throw exception(OUTBOUND_ORDER_REVERSE_INSUFFICIENT);
        }
        try {
            return RdOutboundQuantityCalculator.cancel(stockLot.getWarehouseQty(),
                    zeroIfNull(stockLot.getLockedQty()), zeroIfNull(stockLot.getOutboundQty()),
                    fulfillment.getAllocatedQty(), zeroIfNull(fulfillment.getOutboundQty()),
                    outboundOrder.getQuantity());
        } catch (IllegalArgumentException ex) {
            throw exception(OUTBOUND_ORDER_REVERSE_INSUFFICIENT);
        }
    }

    static boolean isStockUnavailable(String status) {
        return RdStockLotStatusEnum.CLOSED.getStatus().equals(status)
                || RdStockLotStatusEnum.EMPTY.getStatus().equals(status);
    }

    private static void applyStockResult(RdStockLotDO stockLot,
                                         RdOutboundQuantityCalculator.Result result, LocalDate bizDate) {
        stockLot.setWarehouseQty(result.getWarehouseQty())
                .setLockedQty(result.getLockedQty())
                .setOutboundQty(result.getStockOutboundQty())
                .setStockWeight(result.getWarehouseQty().multiply(stockLot.getPackageWeight()))
                .setLastBizDate(maxBizDate(stockLot.getLastBizDate(), bizDate))
                .setStatus(resolveStockStatus(result.getWarehouseQty(), result.getLockedQty()));
    }

    private static String resolveStockStatus(BigDecimal warehouseQty, BigDecimal lockedQty) {
        if (warehouseQty.signum() == 0) {
            return RdStockLotStatusEnum.EMPTY.getStatus();
        }
        if (lockedQty.signum() > 0 && lockedQty.compareTo(warehouseQty) >= 0) {
            return RdStockLotStatusEnum.LOCKED.getStatus();
        }
        return RdStockLotStatusEnum.AVAILABLE.getStatus();
    }

    private static RdOutboundOrderDO buildOutboundOrder(RdOutboundOrderSaveReqVO reqVO,
                                                        RdStockLotDO stockLot) {
        return new RdOutboundOrderDO()
                .setOutboundNo(reqVO.getOutboundNo())
                .setOutboundDate(reqVO.getOutboundDate())
                .setOrderId(reqVO.getOrderId())
                .setFulfillmentId(reqVO.getFulfillmentId())
                .setQuantity(reqVO.getQuantity())
                .setPackageWeight(stockLot.getPackageWeight())
                .setWeight(reqVO.getQuantity().multiply(stockLot.getPackageWeight()))
                .setRemark(reqVO.getRemark());
    }

    private void createOutboundTransaction(RdOutboundOrderDO outboundOrder, RdStockLotDO stockLot,
                                           BigDecimal quantity, BigDecimal beforeQty, BigDecimal afterQty,
                                           String sourceType, Long operatorId, String operatorName) {
        stockTransactionService.createStockTransaction(new RdStockTransactionDO()
                .setBizDate(outboundOrder.getOutboundDate())
                .setTransactionType(RdStockTransactionTypeEnum.OUTBOUND.getType())
                .setStockLotId(stockLot.getId())
                .setLocationId(stockLot.getLocationId())
                .setLocationCode(stockLot.getLocationCode())
                .setCustomerName(stockLot.getCustomerName())
                .setProductModel(stockLot.getProductModel())
                .setFurnaceNo(stockLot.getFurnaceNo())
                .setFurnaceLayer(stockLot.getFurnaceLayer())
                .setQuantity(quantity)
                .setPackageWeight(stockLot.getPackageWeight())
                .setWeight(quantity.multiply(stockLot.getPackageWeight()))
                .setBeforeQty(beforeQty)
                .setAfterQty(afterQty)
                .setSourceType(sourceType)
                .setSourceNo(outboundOrder.getOutboundNo())
                .setSourceLineNo(stockLot.getFurnaceLayer())
                .setRelatedOrderId(outboundOrder.getOrderId())
                .setOperatorId(operatorId)
                .setOperatorName(operatorName)
                .setRemark(SOURCE_TYPE_OUTBOUND_REVERSAL.equals(sourceType)
                        ? "出库撤销反向流水" : "出库确认流水"));
    }

    private static LocalDate maxBizDate(LocalDate currentBizDate, LocalDate newBizDate) {
        return currentBizDate == null || newBizDate.isAfter(currentBizDate) ? newBizDate : currentBizDate;
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

}
