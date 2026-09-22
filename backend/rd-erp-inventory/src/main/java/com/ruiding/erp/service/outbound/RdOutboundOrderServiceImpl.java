package com.ruiding.erp.service.outbound;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.ruiding.erp.controller.admin.outbound.vo.RdOutboundOrderPageReqVO;
import com.ruiding.erp.controller.admin.outbound.vo.RdOutboundOrderSaveReqVO;
import com.ruiding.erp.controller.admin.outbound.vo.RdOutboundScanReqVO;
import com.ruiding.erp.dal.dataobject.order.RdOrderFulfillmentDO;
import com.ruiding.erp.dal.dataobject.order.RdSaleOrderDO;
import com.ruiding.erp.dal.dataobject.outbound.RdOutboundOrderDO;
import com.ruiding.erp.dal.dataobject.outbound.RdOutboundOrderLineDO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockLotDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockTransactionDO;
import com.ruiding.erp.dal.mysql.order.RdOrderFulfillmentMapper;
import com.ruiding.erp.dal.mysql.loading.RdLoadingPackageClaimMapper;
import com.ruiding.erp.dal.mysql.outbound.RdOutboundOrderMapper;
import com.ruiding.erp.dal.mysql.outbound.RdOutboundOrderLineMapper;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.dal.mysql.stock.RdStockLotMapper;
import com.ruiding.erp.dal.mysql.stock.RdStockTransactionMapper;
import com.ruiding.erp.enums.RdLogRecordConstants;
import com.ruiding.erp.enums.RdOrderFulfillmentStatusEnum;
import com.ruiding.erp.enums.RdOrderStatusEnum;
import com.ruiding.erp.enums.RdOutboundOrderStatusEnum;
import com.ruiding.erp.enums.RdPackageStatusEnum;
import com.ruiding.erp.enums.RdStockLotStatusEnum;
import com.ruiding.erp.enums.RdStockTransactionTypeEnum;
import com.ruiding.erp.service.order.RdSaleOrderService;
import com.ruiding.erp.service.masterdata.RdWarehouseLocationFreezeGuard;
import com.ruiding.erp.service.packageunit.RdScanEventService;
import com.ruiding.erp.service.stock.RdStockTransactionService;
import com.mzt.logapi.context.LogRecordContext;
import com.mzt.logapi.starter.annotation.LogRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.ruiding.erp.enums.ErrorCodeConstants.ORDER_FULFILLMENT_NOT_EXISTS;
import static com.ruiding.erp.enums.ErrorCodeConstants.OUTBOUND_ORDER_DRAFT_EXISTS;
import static com.ruiding.erp.enums.ErrorCodeConstants.OUTBOUND_ORDER_FULFILLMENT_MISMATCH;
import static com.ruiding.erp.enums.ErrorCodeConstants.OUTBOUND_ORDER_NOT_EXISTS;
import static com.ruiding.erp.enums.ErrorCodeConstants.OUTBOUND_ORDER_NO_EXISTS;
import static com.ruiding.erp.enums.ErrorCodeConstants.OUTBOUND_ORDER_QUANTITY_EXCEEDS_ALLOCATION;
import static com.ruiding.erp.enums.ErrorCodeConstants.OUTBOUND_ORDER_REVERSE_INSUFFICIENT;
import static com.ruiding.erp.enums.ErrorCodeConstants.OUTBOUND_ORDER_EMPTY;
import static com.ruiding.erp.enums.ErrorCodeConstants.OUTBOUND_ORDER_SELF_REVIEW;
import static com.ruiding.erp.enums.ErrorCodeConstants.OUTBOUND_ORDER_STATUS_INVALID;
import static com.ruiding.erp.enums.ErrorCodeConstants.OUTBOUND_ORDER_VERSION_CONFLICT;
import static com.ruiding.erp.enums.ErrorCodeConstants.OUTBOUND_PACKAGE_DUPLICATE;
import static com.ruiding.erp.enums.ErrorCodeConstants.OUTBOUND_PACKAGE_MISMATCH;
import static com.ruiding.erp.enums.ErrorCodeConstants.OUTBOUND_PACKAGE_NOT_EXISTS;
import static com.ruiding.erp.enums.ErrorCodeConstants.OUTBOUND_PACKAGE_REMOVE_INVALID;
import static com.ruiding.erp.enums.ErrorCodeConstants.OUTBOUND_PACKAGE_STATUS_INVALID;
import static com.ruiding.erp.enums.ErrorCodeConstants.OUTBOUND_ORDER_STATUS_CANNOT_CANCEL;
import static com.ruiding.erp.enums.ErrorCodeConstants.OUTBOUND_ORDER_STATUS_NOT_DRAFT;
import static com.ruiding.erp.enums.ErrorCodeConstants.OUTBOUND_ORDER_STOCK_INSUFFICIENT;
import static com.ruiding.erp.enums.ErrorCodeConstants.SALE_ORDER_STATUS_CANNOT_CHANGE;
import static com.ruiding.erp.enums.ErrorCodeConstants.STOCK_LOT_NOT_EXISTS;
import static com.ruiding.erp.enums.ErrorCodeConstants.LOADING_PACKAGE_CLAIMED;

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
    private RdOutboundOrderLineMapper outboundOrderLineMapper;
    @Resource
    private RdOrderFulfillmentMapper fulfillmentMapper;
    @Resource
    private RdStockLotMapper stockLotMapper;
    @Resource
    private RdPackageUnitMapper packageUnitMapper;
    @Resource
    private RdLoadingPackageClaimMapper loadingClaimMapper;
    @Resource
    private RdStockTransactionMapper stockTransactionMapper;
    @Resource
    private RdSaleOrderService saleOrderService;
    @Resource
    private RdStockTransactionService stockTransactionService;
    @Resource
    private RdScanEventService scanEventService;
    @Resource
    private RdWarehouseLocationFreezeGuard locationFreezeGuard;

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
    public RdOutboundOrderLineDO scanPackage(RdOutboundScanReqVO reqVO, Long operatorId, String operatorName) {
        RdPackageUnitDO unit = null;
        try {
            RdOutboundOrderDO order = outboundOrderMapper.selectByIdForUpdate(reqVO.getId());
            if (order == null) throw exception(OUTBOUND_ORDER_NOT_EXISTS);
            if (!RdOutboundOrderStatusEnum.DRAFT.getStatus().equals(order.getStatus())
                    && !RdOutboundOrderStatusEnum.SCANNING.getStatus().equals(order.getStatus())) {
                throw exception(OUTBOUND_ORDER_STATUS_INVALID);
            }
            unit = packageUnitMapper.selectByPackageCodeForUpdate(reqVO.getPackageCode().trim());
            if (unit == null) throw exception(OUTBOUND_PACKAGE_NOT_EXISTS);
            if (!RdPackageStatusEnum.IN_STOCK.getStatus().equals(unit.getStatus())) {
                throw exception(OUTBOUND_PACKAGE_STATUS_INVALID);
            }
            if (loadingClaimMapper.isClaimed(unit.getTenantId(), unit.getId())) {
                throw exception(LOADING_PACKAGE_CLAIMED);
            }
            locationFreezeGuard.requireMovable(unit.getLocationId());
            if (outboundOrderLineMapper.selectByOrderIdAndPackageId(order.getId(), unit.getId()) != null) {
                throw exception(OUTBOUND_PACKAGE_DUPLICATE);
            }
            RdSaleOrderDO saleOrder = saleOrderService.getSaleOrder(order.getOrderId());
            RdOrderFulfillmentDO fulfillment = fulfillmentMapper.selectById(order.getFulfillmentId());
            if (fulfillment == null || !Objects.equals(fulfillment.getOrderId(), order.getOrderId())
                    || !Objects.equals(fulfillment.getStockLotId(), order.getStockLotId())
                    || !Objects.equals(unit.getStockLotId(), order.getStockLotId())
                    || !Objects.equals(unit.getCustomerName(), saleOrder.getCustomerName())
                    || !Objects.equals(unit.getProductModel(), saleOrder.getProductModel())) {
                throw exception(OUTBOUND_PACKAGE_MISMATCH);
            }
            BigDecimal scannedQty = BigDecimal.valueOf(outboundOrderLineMapper.selectListByOrderId(order.getId()).size() + 1L);
            validateRemainingQuantity(fulfillment, scannedQty);
            RdOutboundOrderLineDO line = new RdOutboundOrderLineDO().setOrderId(order.getId())
                    .setPackageId(unit.getId()).setPackageCode(unit.getPackageCode()).setStockLotId(unit.getStockLotId())
                    .setLocationId(unit.getLocationId()).setLocationCode(unit.getLocationCode())
                    .setCustomerName(unit.getCustomerName()).setProductModel(unit.getProductModel())
                    .setFurnaceNo(unit.getFurnaceNo()).setFurnaceLayer(unit.getFurnaceLayer())
                    .setPackageWeight(unit.getPackageWeight()).setScanDevice(reqVO.getDeviceCode())
                    .setScannerId(operatorId).setScannerName(operatorName);
            outboundOrderLineMapper.insert(line);
            unit.setStatus(RdPackageStatusEnum.OUTBOUND_PENDING.getStatus());
            packageUnitMapper.updateById(unit);
            order.setStatus(RdOutboundOrderStatusEnum.SCANNING.getStatus()).setQuantity(scannedQty)
                    .setPackageWeight(unit.getPackageWeight())
                    .setWeight(sumLineWeight(outboundOrderLineMapper.selectListByOrderId(order.getId()), line))
                    .setVersion(nextVersion(order));
            outboundOrderMapper.updateById(order);
            scanEventService.record("OUTBOUND", order.getId(), unit.getId(), unit.getPackageCode(),
                    reqVO.getDeviceCode(), operatorId, operatorName, true, null);
            return line;
        } catch (RuntimeException ex) {
            scanEventService.record("OUTBOUND", reqVO.getId(), unit == null ? null : unit.getId(),
                    reqVO.getPackageCode(), reqVO.getDeviceCode(), operatorId, operatorName, false, ex.getMessage());
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeScannedPackage(Long id, Long packageId) {
        RdOutboundOrderDO order = outboundOrderMapper.selectByIdForUpdate(id);
        if (order == null) throw exception(OUTBOUND_ORDER_NOT_EXISTS);
        if (!RdOutboundOrderStatusEnum.DRAFT.getStatus().equals(order.getStatus())
                && !RdOutboundOrderStatusEnum.SCANNING.getStatus().equals(order.getStatus())) {
            throw exception(OUTBOUND_PACKAGE_REMOVE_INVALID);
        }
        RdOutboundOrderLineDO line = outboundOrderLineMapper.selectByOrderIdAndPackageId(id, packageId);
        if (line == null) throw exception(OUTBOUND_PACKAGE_NOT_EXISTS);
        RdPackageUnitDO unit = packageUnitMapper.selectByIdForUpdate(packageId);
        if (unit == null || !RdPackageStatusEnum.OUTBOUND_PENDING.getStatus().equals(unit.getStatus())) {
            throw exception(OUTBOUND_PACKAGE_STATUS_INVALID);
        }
        outboundOrderLineMapper.deleteById(line.getId());
        unit.setStatus(RdPackageStatusEnum.IN_STOCK.getStatus());
        packageUnitMapper.updateById(unit);
        List<RdOutboundOrderLineDO> remaining = outboundOrderLineMapper.selectListByOrderId(id);
        order.setQuantity(BigDecimal.valueOf(remaining.size())).setWeight(sumLineWeight(remaining, null))
                .setStatus(remaining.isEmpty() ? RdOutboundOrderStatusEnum.DRAFT.getStatus()
                        : RdOutboundOrderStatusEnum.SCANNING.getStatus())
                .setVersion(nextVersion(order));
        outboundOrderMapper.updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitOutboundOrder(Long id, Integer version, Long operatorId, String operatorName) {
        RdOutboundOrderDO order = outboundOrderMapper.selectByIdForUpdate(id);
        requireStatus(order, RdOutboundOrderStatusEnum.SCANNING);
        requireVersion(order, version);
        List<RdOutboundOrderLineDO> lines = outboundOrderLineMapper.selectListByOrderId(id);
        if (lines.isEmpty()) throw exception(OUTBOUND_ORDER_EMPTY);
        RdOrderFulfillmentDO fulfillment = fulfillmentMapper.selectById(order.getFulfillmentId());
        validateRemainingQuantity(fulfillment, BigDecimal.valueOf(lines.size()));
        order.setQuantity(BigDecimal.valueOf(lines.size())).setWeight(sumLineWeight(lines, null))
                .setStatus(RdOutboundOrderStatusEnum.SUBMITTED.getStatus()).setSubmitterId(operatorId)
                .setSubmitterName(operatorName).setSubmitTime(LocalDateTime.now()).setVersion(nextVersion(order));
        outboundOrderMapper.updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveOutboundOrder(Long id, Integer version, Long reviewerId, String reviewerName) {
        RdOutboundOrderDO order = outboundOrderMapper.selectByIdForUpdate(id);
        requireStatus(order, RdOutboundOrderStatusEnum.SUBMITTED);
        requireVersion(order, version);
        if (Objects.equals(reviewerId, order.getSubmitterId())) throw exception(OUTBOUND_ORDER_SELF_REVIEW);
        order.setStatus(RdOutboundOrderStatusEnum.APPROVED.getStatus()).setReviewerId(reviewerId)
                .setReviewerName(reviewerName).setReviewTime(LocalDateTime.now()).setVersion(nextVersion(order));
        outboundOrderMapper.updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void postOutboundOrder(Long id, Integer version, Long operatorId, String operatorName) {
        RdOutboundOrderDO order = outboundOrderMapper.selectByIdForUpdate(id);
        if (order != null && RdOutboundOrderStatusEnum.POSTED.getStatus().equals(order.getStatus())) return;
        requireStatus(order, RdOutboundOrderStatusEnum.APPROVED);
        requireVersion(order, version);
        saleOrderService.lockSaleOrderForOutbound(order.getOrderId(), false);
        RdOrderFulfillmentDO fulfillment = fulfillmentMapper.selectByIdForUpdate(order.getFulfillmentId());
        List<RdOutboundOrderLineDO> lines = outboundOrderLineMapper.selectListByOrderId(id);
        if (lines.isEmpty()) throw exception(OUTBOUND_ORDER_EMPTY);
        validateRemainingQuantity(fulfillment, BigDecimal.valueOf(lines.size()));
        for (RdOutboundOrderLineDO line : lines) postPackage(order, line, operatorId, operatorName);
        BigDecimal quantity = BigDecimal.valueOf(lines.size());
        BigDecimal outboundQty = zeroIfNull(fulfillment.getOutboundQty()).add(quantity);
        fulfillment.setOutboundQty(outboundQty).setStatus(outboundQty.compareTo(fulfillment.getAllocatedQty()) >= 0
                ? RdOrderFulfillmentStatusEnum.OUTBOUNDED.getStatus() : RdOrderFulfillmentStatusEnum.ALLOCATED.getStatus());
        fulfillmentMapper.updateById(fulfillment);
        order.setQuantity(quantity).setWeight(sumLineWeight(lines, null)).setStatus(RdOutboundOrderStatusEnum.POSTED.getStatus())
                .setPosterId(operatorId).setPosterName(operatorName).setPostTime(LocalDateTime.now())
                .setVersion(nextVersion(order));
        outboundOrderMapper.updateById(order);
        saleOrderService.syncOutboundStatus(order.getOrderId(),
                fulfillmentMapper.sumOutboundQtyByOrderId(order.getOrderId()), operatorName);
    }

    private void postPackage(RdOutboundOrderDO order, RdOutboundOrderLineDO line,
                             Long operatorId, String operatorName) {
        String key = "OUTBOUND:" + order.getId() + ":" + line.getPackageId();
        if (stockTransactionMapper.selectByIdempotencyKey(key) != null) return;
        RdPackageUnitDO unit = packageUnitMapper.selectByIdForUpdate(line.getPackageId());
        if (unit == null || !RdPackageStatusEnum.OUTBOUND_PENDING.getStatus().equals(unit.getStatus())
                || !Objects.equals(unit.getStockLotId(), line.getStockLotId())) {
            throw exception(OUTBOUND_PACKAGE_STATUS_INVALID);
        }
        locationFreezeGuard.requireMovable(unit.getLocationId());
        RdStockLotDO lot = stockLotMapper.selectByIdForUpdate(line.getStockLotId());
        if (lot == null) throw exception(STOCK_LOT_NOT_EXISTS);
        BigDecimal before = zeroIfNull(lot.getWarehouseQty());
        BigDecimal after = before.subtract(BigDecimal.ONE);
        BigDecimal lockedAfter = zeroIfNull(lot.getLockedQty()).subtract(BigDecimal.ONE);
        if (after.signum() < 0 || lockedAfter.signum() < 0) throw exception(OUTBOUND_ORDER_STOCK_INSUFFICIENT);
        lot.setWarehouseQty(after).setLockedQty(lockedAfter)
                .setOutboundQty(zeroIfNull(lot.getOutboundQty()).add(BigDecimal.ONE))
                .setStockWeight(zeroIfNull(lot.getStockWeight()).subtract(unit.getPackageWeight()))
                .setLastBizDate(maxBizDate(lot.getLastBizDate(), order.getOutboundDate()))
                .setStatus(resolveStockStatus(after, lockedAfter));
        stockLotMapper.updateById(lot);
        RdStockTransactionDO tx = new RdStockTransactionDO().setBizDate(order.getOutboundDate())
                .setTransactionType(RdStockTransactionTypeEnum.OUTBOUND.getType()).setStockLotId(lot.getId())
                .setPackageId(unit.getId()).setLocationId(unit.getLocationId()).setLocationCode(unit.getLocationCode())
                .setCustomerName(unit.getCustomerName()).setProductModel(unit.getProductModel())
                .setFurnaceNo(unit.getFurnaceNo()).setFurnaceLayer(unit.getFurnaceLayer())
                .setQuantity(BigDecimal.ONE.negate()).setPackageWeight(unit.getPackageWeight())
                .setWeight(unit.getPackageWeight().negate()).setBeforeQty(before).setAfterQty(after)
                .setSourceType(SOURCE_TYPE_OUTBOUND_ORDER).setSourceNo(order.getOutboundNo())
                .setSourceLineNo(unit.getPackageCode()).setRelatedOrderId(order.getOrderId())
                .setOperatorId(operatorId).setOperatorName(operatorName).setReviewerId(order.getReviewerId())
                .setReviewerName(order.getReviewerName()).setIdempotencyKey(key).setRemark("包码扫码出库");
        stockTransactionMapper.insert(tx);
        unit.setStatus(RdPackageStatusEnum.OUTBOUNDED.getStatus());
        packageUnitMapper.updateById(unit);
        line.setStockTransactionId(tx.getId());
        outboundOrderLineMapper.updateById(line);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reverseOutboundOrder(Long id, Integer version, Long operatorId, String operatorName, String reason) {
        RdOutboundOrderDO order = outboundOrderMapper.selectByIdForUpdate(id);
        requireStatus(order, RdOutboundOrderStatusEnum.POSTED);
        requireVersion(order, version);
        saleOrderService.lockSaleOrderForOutbound(order.getOrderId(), true);
        RdOrderFulfillmentDO fulfillment = fulfillmentMapper.selectByIdForUpdate(order.getFulfillmentId());
        List<RdOutboundOrderLineDO> lines = outboundOrderLineMapper.selectListByOrderId(id);
        for (RdOutboundOrderLineDO line : lines) reversePackage(order, line, operatorId, operatorName, reason);
        BigDecimal quantity = BigDecimal.valueOf(lines.size());
        BigDecimal outboundQty = zeroIfNull(fulfillment.getOutboundQty()).subtract(quantity);
        if (outboundQty.signum() < 0) throw exception(OUTBOUND_ORDER_REVERSE_INSUFFICIENT);
        fulfillment.setOutboundQty(outboundQty).setStatus(RdOrderFulfillmentStatusEnum.ALLOCATED.getStatus());
        fulfillmentMapper.updateById(fulfillment);
        order.setStatus(RdOutboundOrderStatusEnum.REVERSED.getStatus()).setReverserId(operatorId)
                .setReverserName(operatorName).setReverseTime(LocalDateTime.now()).setReverseReason(reason)
                .setVersion(nextVersion(order));
        outboundOrderMapper.updateById(order);
        saleOrderService.syncOutboundStatus(order.getOrderId(),
                fulfillmentMapper.sumOutboundQtyByOrderId(order.getOrderId()), operatorName);
    }

    private void reversePackage(RdOutboundOrderDO order, RdOutboundOrderLineDO line,
                                Long operatorId, String operatorName, String reason) {
        String key = "OUTBOUND-REVERSE:" + order.getId() + ":" + line.getPackageId();
        if (stockTransactionMapper.selectByIdempotencyKey(key) != null) return;
        RdPackageUnitDO unit = packageUnitMapper.selectByIdForUpdate(line.getPackageId());
        if (unit == null || !RdPackageStatusEnum.OUTBOUNDED.getStatus().equals(unit.getStatus())) {
            throw exception(OUTBOUND_ORDER_REVERSE_INSUFFICIENT);
        }
        locationFreezeGuard.requireMovable(unit.getLocationId());
        RdStockLotDO lot = stockLotMapper.selectByIdForUpdate(line.getStockLotId());
        if (lot == null || RdStockLotStatusEnum.CLOSED.getStatus().equals(lot.getStatus())) {
            throw exception(OUTBOUND_ORDER_REVERSE_INSUFFICIENT);
        }
        BigDecimal before = zeroIfNull(lot.getWarehouseQty());
        BigDecimal after = before.add(BigDecimal.ONE);
        lot.setWarehouseQty(after).setLockedQty(zeroIfNull(lot.getLockedQty()).add(BigDecimal.ONE))
                .setOutboundQty(zeroIfNull(lot.getOutboundQty()).subtract(BigDecimal.ONE))
                .setStockWeight(zeroIfNull(lot.getStockWeight()).add(unit.getPackageWeight()))
                .setStatus(RdStockLotStatusEnum.LOCKED.getStatus());
        stockLotMapper.updateById(lot);
        RdStockTransactionDO original = stockTransactionMapper.selectById(line.getStockTransactionId());
        stockTransactionMapper.insert(new RdStockTransactionDO().setBizDate(LocalDate.now())
                .setTransactionType(RdStockTransactionTypeEnum.ADJUST_IN.getType()).setStockLotId(lot.getId())
                .setPackageId(unit.getId()).setLocationId(unit.getLocationId()).setLocationCode(unit.getLocationCode())
                .setCustomerName(unit.getCustomerName()).setProductModel(unit.getProductModel())
                .setFurnaceNo(unit.getFurnaceNo()).setFurnaceLayer(unit.getFurnaceLayer())
                .setQuantity(BigDecimal.ONE).setPackageWeight(unit.getPackageWeight()).setWeight(unit.getPackageWeight())
                .setBeforeQty(before).setAfterQty(after).setSourceType(SOURCE_TYPE_OUTBOUND_REVERSAL)
                .setSourceNo(order.getOutboundNo()).setSourceLineNo(unit.getPackageCode())
                .setRelatedOrderId(order.getOrderId()).setOperatorId(operatorId).setOperatorName(operatorName)
                .setReviewerId(order.getReviewerId()).setReviewerName(order.getReviewerName())
                .setIdempotencyKey(key).setReversalTransactionId(original == null ? null : original.getId())
                .setRemark(reason));
        unit.setStatus(RdPackageStatusEnum.IN_STOCK.getStatus());
        packageUnitMapper.updateById(unit);
    }

    @Override
    public List<RdOutboundOrderLineDO> getOutboundOrderLines(Long id) {
        getOutboundOrder(id);
        return outboundOrderLineMapper.selectListByOrderId(id);
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
        locationFreezeGuard.requireMovable(stockLot == null ? null : stockLot.getLocationId());
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
        locationFreezeGuard.requireMovable(stockLot == null ? null : stockLot.getLocationId());
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
        if (reqVO.getQuantity() != null && reqVO.getQuantity().signum() > 0) {
            validateRemainingQuantity(fulfillment, reqVO.getQuantity());
        }
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
                .setQuantity(BigDecimal.ZERO)
                .setPackageWeight(stockLot.getPackageWeight())
                .setWeight(BigDecimal.ZERO)
                .setVersion(0)
                .setRemark(reqVO.getRemark());
    }

    private static void requireStatus(RdOutboundOrderDO order, RdOutboundOrderStatusEnum status) {
        if (order == null) throw exception(OUTBOUND_ORDER_NOT_EXISTS);
        if (!status.getStatus().equals(order.getStatus())) throw exception(OUTBOUND_ORDER_STATUS_INVALID);
    }

    private static void requireVersion(RdOutboundOrderDO order, Integer version) {
        if (version != null && !Objects.equals(zeroIfNull(order.getVersion()), version)) {
            throw exception(OUTBOUND_ORDER_VERSION_CONFLICT);
        }
    }

    private static int nextVersion(RdOutboundOrderDO order) {
        return zeroIfNull(order.getVersion()) + 1;
    }

    private static int zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }

    private static BigDecimal sumLineWeight(List<RdOutboundOrderLineDO> lines, RdOutboundOrderLineDO additional) {
        BigDecimal total = BigDecimal.ZERO;
        for (RdOutboundOrderLineDO line : lines) {
            total = total.add(zeroIfNull(line.getPackageWeight()));
        }
        if (additional != null && lines.stream().noneMatch(line -> Objects.equals(line.getPackageId(), additional.getPackageId()))) {
            total = total.add(zeroIfNull(additional.getPackageWeight()));
        }
        return total;
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
