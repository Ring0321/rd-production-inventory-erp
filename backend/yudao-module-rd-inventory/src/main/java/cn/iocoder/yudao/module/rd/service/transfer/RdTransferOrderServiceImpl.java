package cn.iocoder.yudao.module.rd.service.transfer;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rd.controller.admin.transfer.vo.RdTransferOrderPageReqVO;
import cn.iocoder.yudao.module.rd.controller.admin.transfer.vo.RdTransferOrderSaveReqVO;
import cn.iocoder.yudao.module.rd.dal.dataobject.order.RdSaleOrderDO;
import cn.iocoder.yudao.module.rd.dal.dataobject.stock.RdStockLotDO;
import cn.iocoder.yudao.module.rd.dal.dataobject.stock.RdStockTransactionDO;
import cn.iocoder.yudao.module.rd.dal.dataobject.transfer.RdTransferOrderDO;
import cn.iocoder.yudao.module.rd.dal.mysql.stock.RdStockLotMapper;
import cn.iocoder.yudao.module.rd.dal.mysql.transfer.RdTransferOrderMapper;
import cn.iocoder.yudao.module.rd.enums.RdLogRecordConstants;
import cn.iocoder.yudao.module.rd.enums.RdStockLotStatusEnum;
import cn.iocoder.yudao.module.rd.enums.RdStockTransactionTypeEnum;
import cn.iocoder.yudao.module.rd.enums.RdTransferOrderStatusEnum;
import cn.iocoder.yudao.module.rd.service.order.RdSaleOrderService;
import cn.iocoder.yudao.module.rd.service.stock.RdStockTransactionService;
import com.mzt.logapi.context.LogRecordContext;
import com.mzt.logapi.starter.annotation.LogRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.STOCK_LOT_NOT_EXISTS;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.TRANSFER_ORDER_FROM_ORDER_MISMATCH;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.TRANSFER_ORDER_MODEL_MISMATCH;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.TRANSFER_ORDER_NOT_EXISTS;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.TRANSFER_ORDER_NO_EXISTS;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.TRANSFER_ORDER_REVERSE_INSUFFICIENT;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.TRANSFER_ORDER_SAME_CUSTOMER;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.TRANSFER_ORDER_STATUS_CANNOT_CANCEL;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.TRANSFER_ORDER_STATUS_NOT_DRAFT;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.TRANSFER_ORDER_STOCK_INSUFFICIENT;

/**
 * RD transfer order Service implementation.
 */
@Service
@Validated
public class RdTransferOrderServiceImpl implements RdTransferOrderService {

    private static final String SOURCE_TYPE_TRANSFER_ORDER = "TRANSFER_ORDER";
    private static final String SOURCE_TYPE_TRANSFER_REVERSAL = "TRANSFER_REVERSAL";

    @Resource
    private RdTransferOrderMapper transferOrderMapper;
    @Resource
    private RdStockLotMapper stockLotMapper;
    @Resource
    private RdStockTransactionService stockTransactionService;
    @Resource
    private RdSaleOrderService saleOrderService;

    @Override
    @LogRecord(type = RdLogRecordConstants.TRANSFER_TYPE,
            subType = RdLogRecordConstants.TRANSFER_CREATE_SUB_TYPE,
            bizNo = RdLogRecordConstants.BIZ_ID_EXPRESSION,
            success = RdLogRecordConstants.TRANSFER_CREATE_SUCCESS)
    public Long createTransferOrder(RdTransferOrderSaveReqVO createReqVO) {
        validateTransferNoUnique(null, createReqVO.getTransferNo());
        RdStockLotDO fromStockLot = stockLotMapper.selectById(createReqVO.getFromStockLotId());
        validateTransferBusiness(createReqVO, fromStockLot);
        RdTransferOrderDO transferOrder = buildTransferOrder(createReqVO, fromStockLot)
                .setStatus(RdTransferOrderStatusEnum.DRAFT.getStatus());
        transferOrderMapper.insert(transferOrder);
        LogRecordContext.putVariable(RdLogRecordConstants.BIZ_ID_VARIABLE, transferOrder.getId());
        return transferOrder.getId();
    }

    @Override
    @LogRecord(type = RdLogRecordConstants.TRANSFER_TYPE,
            subType = RdLogRecordConstants.TRANSFER_UPDATE_SUB_TYPE,
            bizNo = "{{#updateReqVO.id}}", success = RdLogRecordConstants.TRANSFER_UPDATE_SUCCESS)
    public void updateTransferOrder(RdTransferOrderSaveReqVO updateReqVO) {
        RdTransferOrderDO transferOrder = validateTransferOrderDraft(updateReqVO.getId());
        validateTransferNoUnique(updateReqVO.getId(), updateReqVO.getTransferNo());
        RdStockLotDO fromStockLot = stockLotMapper.selectById(updateReqVO.getFromStockLotId());
        validateTransferBusiness(updateReqVO, fromStockLot);
        RdTransferOrderDO updateObj = buildTransferOrder(updateReqVO, fromStockLot)
                .setId(transferOrder.getId());
        transferOrderMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = RdLogRecordConstants.TRANSFER_TYPE,
            subType = RdLogRecordConstants.TRANSFER_CONFIRM_SUB_TYPE,
            bizNo = "{{#id}}", success = RdLogRecordConstants.TRANSFER_CONFIRM_SUCCESS)
    public void confirmTransferOrder(Long id, Long operatorId, String operatorName) {
        RdTransferOrderDO transferOrder = transferOrderMapper.selectByIdForUpdate(id);
        if (transferOrder == null) {
            throw exception(TRANSFER_ORDER_NOT_EXISTS);
        }
        if (!RdTransferOrderStatusEnum.DRAFT.getStatus().equals(transferOrder.getStatus())) {
            throw exception(TRANSFER_ORDER_STATUS_NOT_DRAFT);
        }

        saleOrderService.changeTransferQty(
                transferOrder.getFromOrderId(), transferOrder.getToOrderId(), transferOrder.getQuantity());
        RdStockLotDO fromStockLot = stockLotMapper.selectByIdForUpdate(transferOrder.getFromStockLotId());
        validateTransferBusiness(transferOrder, fromStockLot);

        BigDecimal fromBeforeQty = fromStockLot.getWarehouseQty();
        BigDecimal fromAfterQty = fromBeforeQty.subtract(transferOrder.getQuantity());
        fromStockLot.setTransferOutQty(zeroIfNull(fromStockLot.getTransferOutQty()).add(transferOrder.getQuantity()))
                .setWarehouseQty(fromAfterQty)
                .setStockWeight(fromAfterQty.multiply(fromStockLot.getPackageWeight()))
                .setLastBizDate(maxBizDate(fromStockLot.getLastBizDate(), transferOrder.getTransferDate()));
        if (fromAfterQty.signum() == 0) {
            fromStockLot.setStatus(RdStockLotStatusEnum.EMPTY.getStatus());
        }
        stockLotMapper.updateById(fromStockLot);

        RdSaleOrderDO toOrder = saleOrderService.getSaleOrder(transferOrder.getToOrderId());
        RdStockLotDO toStockLot = createTargetStockLot(transferOrder, fromStockLot, toOrder);
        createTransferTransaction(transferOrder, fromStockLot, RdStockTransactionTypeEnum.TRANSFER_OUT.getType(),
                transferOrder.getQuantity().negate(), fromBeforeQty, fromAfterQty,
                SOURCE_TYPE_TRANSFER_ORDER, transferOrder.getFromOrderId(), operatorId, operatorName);
        createTransferTransaction(transferOrder, toStockLot, RdStockTransactionTypeEnum.TRANSFER_IN.getType(),
                transferOrder.getQuantity(), BigDecimal.ZERO, transferOrder.getQuantity(),
                SOURCE_TYPE_TRANSFER_ORDER, transferOrder.getToOrderId(), operatorId, operatorName);

        transferOrderMapper.updateById(new RdTransferOrderDO()
                .setId(transferOrder.getId())
                .setToStockLotId(toStockLot.getId())
                .setStatus(RdTransferOrderStatusEnum.CONFIRMED.getStatus()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = RdLogRecordConstants.TRANSFER_TYPE,
            subType = RdLogRecordConstants.TRANSFER_CANCEL_SUB_TYPE,
            bizNo = "{{#id}}", success = RdLogRecordConstants.TRANSFER_CANCEL_SUCCESS)
    public void cancelTransferOrder(Long id, Long operatorId, String operatorName) {
        RdTransferOrderDO transferOrder = transferOrderMapper.selectByIdForUpdate(id);
        if (transferOrder == null) {
            throw exception(TRANSFER_ORDER_NOT_EXISTS);
        }
        if (RdTransferOrderStatusEnum.DRAFT.getStatus().equals(transferOrder.getStatus())) {
            transferOrderMapper.updateById(new RdTransferOrderDO()
                    .setId(id)
                    .setStatus(RdTransferOrderStatusEnum.CANCELLED.getStatus()));
            return;
        }
        if (!RdTransferOrderStatusEnum.CONFIRMED.getStatus().equals(transferOrder.getStatus())) {
            throw exception(TRANSFER_ORDER_STATUS_CANNOT_CANCEL);
        }

        saleOrderService.changeTransferQty(
                transferOrder.getFromOrderId(), transferOrder.getToOrderId(), transferOrder.getQuantity().negate());
        RdStockLotDO fromStockLot = stockLotMapper.selectByIdForUpdate(transferOrder.getFromStockLotId());
        RdStockLotDO toStockLot = stockLotMapper.selectByIdForUpdate(transferOrder.getToStockLotId());
        validateReverseStock(transferOrder, fromStockLot, toStockLot);

        LocalDate reverseDate = LocalDate.now();
        BigDecimal fromBeforeQty = fromStockLot.getWarehouseQty();
        BigDecimal fromAfterQty = fromBeforeQty.add(transferOrder.getQuantity());
        fromStockLot.setTransferOutQty(zeroIfNull(fromStockLot.getTransferOutQty())
                        .subtract(transferOrder.getQuantity()))
                .setWarehouseQty(fromAfterQty)
                .setStockWeight(fromAfterQty.multiply(fromStockLot.getPackageWeight()))
                .setLastBizDate(maxBizDate(fromStockLot.getLastBizDate(), reverseDate));
        if (RdStockLotStatusEnum.EMPTY.getStatus().equals(fromStockLot.getStatus())) {
            fromStockLot.setStatus(RdStockLotStatusEnum.AVAILABLE.getStatus());
        }
        stockLotMapper.updateById(fromStockLot);

        BigDecimal toBeforeQty = toStockLot.getWarehouseQty();
        BigDecimal toAfterQty = toBeforeQty.subtract(transferOrder.getQuantity());
        toStockLot.setTransferInQty(zeroIfNull(toStockLot.getTransferInQty())
                        .subtract(transferOrder.getQuantity()))
                .setWarehouseQty(toAfterQty)
                .setStockWeight(toAfterQty.multiply(toStockLot.getPackageWeight()))
                .setLastBizDate(maxBizDate(toStockLot.getLastBizDate(), reverseDate));
        if (toAfterQty.signum() == 0) {
            toStockLot.setStatus(RdStockLotStatusEnum.EMPTY.getStatus());
        }
        stockLotMapper.updateById(toStockLot);

        RdTransferOrderDO reverseTransfer = new RdTransferOrderDO()
                .setId(transferOrder.getId())
                .setTransferNo(transferOrder.getTransferNo())
                .setTransferDate(reverseDate)
                .setFromOrderId(transferOrder.getFromOrderId())
                .setToOrderId(transferOrder.getToOrderId());
        createTransferTransaction(reverseTransfer, fromStockLot, RdStockTransactionTypeEnum.TRANSFER_IN.getType(),
                transferOrder.getQuantity(), fromBeforeQty, fromAfterQty,
                SOURCE_TYPE_TRANSFER_REVERSAL, transferOrder.getFromOrderId(), operatorId, operatorName);
        createTransferTransaction(reverseTransfer, toStockLot, RdStockTransactionTypeEnum.TRANSFER_OUT.getType(),
                transferOrder.getQuantity().negate(), toBeforeQty, toAfterQty,
                SOURCE_TYPE_TRANSFER_REVERSAL, transferOrder.getToOrderId(), operatorId, operatorName);

        transferOrderMapper.updateById(new RdTransferOrderDO()
                .setId(id)
                .setStatus(RdTransferOrderStatusEnum.CANCELLED.getStatus()));
    }

    @Override
    public RdTransferOrderDO getTransferOrder(Long id) {
        RdTransferOrderDO transferOrder = transferOrderMapper.selectById(id);
        if (transferOrder == null) {
            throw exception(TRANSFER_ORDER_NOT_EXISTS);
        }
        return transferOrder;
    }

    @Override
    public PageResult<RdTransferOrderDO> getTransferOrderPage(RdTransferOrderPageReqVO pageReqVO) {
        return transferOrderMapper.selectPage(pageReqVO);
    }

    private RdTransferOrderDO validateTransferOrderDraft(Long id) {
        RdTransferOrderDO transferOrder = getTransferOrder(id);
        if (!RdTransferOrderStatusEnum.DRAFT.getStatus().equals(transferOrder.getStatus())) {
            throw exception(TRANSFER_ORDER_STATUS_NOT_DRAFT);
        }
        return transferOrder;
    }

    private void validateTransferNoUnique(Long id, String transferNo) {
        RdTransferOrderDO existingOrder = transferOrderMapper.selectByTransferNo(transferNo);
        if (existingOrder != null && (id == null || !existingOrder.getId().equals(id))) {
            throw exception(TRANSFER_ORDER_NO_EXISTS);
        }
    }

    private void validateTransferBusiness(RdTransferOrderSaveReqVO reqVO, RdStockLotDO fromStockLot) {
        RdSaleOrderDO toOrder = saleOrderService.getSaleOrder(reqVO.getToOrderId());
        RdSaleOrderDO fromOrder = reqVO.getFromOrderId() == null
                ? null : saleOrderService.getSaleOrder(reqVO.getFromOrderId());
        validateTransferBusiness(reqVO.getFromOrderId(), toOrder, fromOrder, fromStockLot, reqVO.getQuantity());
    }

    private void validateTransferBusiness(RdTransferOrderDO transferOrder, RdStockLotDO fromStockLot) {
        RdSaleOrderDO toOrder = saleOrderService.getSaleOrder(transferOrder.getToOrderId());
        RdSaleOrderDO fromOrder = transferOrder.getFromOrderId() == null
                ? null : saleOrderService.getSaleOrder(transferOrder.getFromOrderId());
        validateTransferBusiness(transferOrder.getFromOrderId(), toOrder, fromOrder,
                fromStockLot, transferOrder.getQuantity());
    }

    private static void validateTransferBusiness(Long fromOrderId, RdSaleOrderDO toOrder,
                                                 RdSaleOrderDO fromOrder, RdStockLotDO fromStockLot,
                                                 BigDecimal quantity) {
        if (fromStockLot == null) {
            throw exception(STOCK_LOT_NOT_EXISTS);
        }
        if (!toOrder.getProductModel().equals(fromStockLot.getProductModel())) {
            throw exception(TRANSFER_ORDER_MODEL_MISMATCH);
        }
        if (toOrder.getCustomerName().equals(fromStockLot.getCustomerName())) {
            throw exception(TRANSFER_ORDER_SAME_CUSTOMER);
        }
        if (fromOrderId != null && (fromOrder == null
                || !fromOrder.getCustomerName().equals(fromStockLot.getCustomerName())
                || !fromOrder.getProductModel().equals(fromStockLot.getProductModel()))) {
            throw exception(TRANSFER_ORDER_FROM_ORDER_MISMATCH);
        }
        BigDecimal availableQty = fromStockLot.getWarehouseQty().subtract(zeroIfNull(fromStockLot.getLockedQty()));
        if (quantity.compareTo(availableQty) > 0
                || RdStockLotStatusEnum.EMPTY.getStatus().equals(fromStockLot.getStatus())
                || RdStockLotStatusEnum.CLOSED.getStatus().equals(fromStockLot.getStatus())) {
            throw exception(TRANSFER_ORDER_STOCK_INSUFFICIENT);
        }
    }

    private static void validateReverseStock(RdTransferOrderDO transferOrder,
                                             RdStockLotDO fromStockLot, RdStockLotDO toStockLot) {
        if (fromStockLot == null || toStockLot == null) {
            throw exception(STOCK_LOT_NOT_EXISTS);
        }
        BigDecimal toAvailableQty = toStockLot.getWarehouseQty().subtract(zeroIfNull(toStockLot.getLockedQty()));
        if (toAvailableQty.compareTo(transferOrder.getQuantity()) < 0
                || RdStockLotStatusEnum.CLOSED.getStatus().equals(toStockLot.getStatus())
                || zeroIfNull(toStockLot.getTransferInQty()).compareTo(transferOrder.getQuantity()) < 0
                || zeroIfNull(fromStockLot.getTransferOutQty()).compareTo(transferOrder.getQuantity()) < 0) {
            throw exception(TRANSFER_ORDER_REVERSE_INSUFFICIENT);
        }
    }

    private static RdTransferOrderDO buildTransferOrder(RdTransferOrderSaveReqVO reqVO,
                                                        RdStockLotDO fromStockLot) {
        return new RdTransferOrderDO()
                .setTransferNo(reqVO.getTransferNo())
                .setTransferDate(reqVO.getTransferDate())
                .setFromOrderId(reqVO.getFromOrderId())
                .setToOrderId(reqVO.getToOrderId())
                .setFromStockLotId(reqVO.getFromStockLotId())
                .setQuantity(reqVO.getQuantity())
                .setPackageWeight(fromStockLot.getPackageWeight())
                .setWeight(reqVO.getQuantity().multiply(fromStockLot.getPackageWeight()))
                .setRemark(reqVO.getRemark());
    }

    private RdStockLotDO createTargetStockLot(RdTransferOrderDO transferOrder,
                                              RdStockLotDO fromStockLot, RdSaleOrderDO toOrder) {
        BigDecimal stockWeight = transferOrder.getQuantity().multiply(fromStockLot.getPackageWeight());
        RdStockLotDO toStockLot = new RdStockLotDO()
                .setLocationId(fromStockLot.getLocationId())
                .setLocationCode(fromStockLot.getLocationCode())
                .setCustomerName(toOrder.getCustomerName())
                .setProductModel(fromStockLot.getProductModel())
                .setFurnaceNo(fromStockLot.getFurnaceNo())
                .setFurnaceLayer(fromStockLot.getFurnaceLayer())
                .setOpeningQty(BigDecimal.ZERO)
                .setTransferInQty(transferOrder.getQuantity())
                .setTransferOutQty(BigDecimal.ZERO)
                .setInboundQty(BigDecimal.ZERO)
                .setOutboundQty(BigDecimal.ZERO)
                .setReturnQty(BigDecimal.ZERO)
                .setWarehouseQty(transferOrder.getQuantity())
                .setLockedQty(BigDecimal.ZERO)
                .setPackageWeight(fromStockLot.getPackageWeight())
                .setStockWeight(stockWeight)
                .setLastBizDate(transferOrder.getTransferDate())
                .setStatus(RdStockLotStatusEnum.AVAILABLE.getStatus())
                .setRemark("由调拨单 " + transferOrder.getTransferNo() + " 生成");
        stockLotMapper.insert(toStockLot);
        return toStockLot;
    }

    private void createTransferTransaction(RdTransferOrderDO transferOrder, RdStockLotDO stockLot,
                                           String transactionType, BigDecimal quantity,
                                           BigDecimal beforeQty, BigDecimal afterQty,
                                           String sourceType, Long relatedOrderId,
                                           Long operatorId, String operatorName) {
        stockTransactionService.createStockTransaction(new RdStockTransactionDO()
                .setBizDate(transferOrder.getTransferDate())
                .setTransactionType(transactionType)
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
                .setSourceNo(transferOrder.getTransferNo())
                .setSourceLineNo(stockLot.getFurnaceLayer())
                .setRelatedOrderId(relatedOrderId)
                .setOperatorId(operatorId)
                .setOperatorName(operatorName)
                .setRemark(sourceType.equals(SOURCE_TYPE_TRANSFER_REVERSAL) ? "调拨撤销反向流水" : "调拨确认流水"));
    }

    private static LocalDate maxBizDate(LocalDate currentBizDate, LocalDate newBizDate) {
        return currentBizDate == null || newBizDate.isAfter(currentBizDate) ? newBizDate : currentBizDate;
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

}
