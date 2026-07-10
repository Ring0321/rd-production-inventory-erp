package cn.iocoder.yudao.module.rd.service.order;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rd.controller.admin.order.vo.RdSaleOrderPageReqVO;
import cn.iocoder.yudao.module.rd.controller.admin.order.vo.RdSaleOrderSaveReqVO;
import cn.iocoder.yudao.module.rd.dal.dataobject.order.RdSaleOrderDO;
import cn.iocoder.yudao.module.rd.dal.mysql.order.RdOrderFulfillmentMapper;
import cn.iocoder.yudao.module.rd.dal.mysql.order.RdSaleOrderMapper;
import cn.iocoder.yudao.module.rd.enums.RdLogRecordConstants;
import cn.iocoder.yudao.module.rd.enums.RdOrderStatusEnum;
import com.mzt.logapi.context.LogRecordContext;
import com.mzt.logapi.starter.annotation.LogRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertMap;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.SALE_ORDER_NOT_EXISTS;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.SALE_ORDER_ALLOCATION_EXCEEDS_DIFFERENCE;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.SALE_ORDER_ALLOCATION_NEGATIVE;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.SALE_ORDER_NO_EXISTS;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.SALE_ORDER_OUTBOUND_EXISTS;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.SALE_ORDER_STATUS_CANNOT_CHANGE;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.SALE_ORDER_TRANSFER_EXCEEDS_DIFFERENCE;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.SALE_ORDER_TRANSFER_NEGATIVE;

/**
 * RD sales order Service implementation.
 */
@Service
@Validated
public class RdSaleOrderServiceImpl implements RdSaleOrderService {

    @Resource
    private RdSaleOrderMapper saleOrderMapper;
    @Resource
    private RdOrderFulfillmentMapper fulfillmentMapper;

    @Override
    @LogRecord(type = RdLogRecordConstants.SALE_ORDER_TYPE,
            subType = RdLogRecordConstants.SALE_ORDER_CREATE_SUB_TYPE,
            bizNo = RdLogRecordConstants.BIZ_ID_EXPRESSION,
            success = RdLogRecordConstants.SALE_ORDER_CREATE_SUCCESS)
    public Long createSaleOrder(RdSaleOrderSaveReqVO createReqVO) {
        validateOrderNoUnique(null, createReqVO.getOrderNo());
        RdSaleOrderDO saleOrder = new RdSaleOrderDO()
                .setOrderNo(createReqVO.getOrderNo())
                .setOrderDate(createReqVO.getOrderDate())
                .setCustomerName(createReqVO.getCustomerName())
                .setProductModel(createReqVO.getProductModel())
                .setRequiredQty(createReqVO.getRequiredQty())
                .setActualOutputQty(zeroIfNull(createReqVO.getActualOutputQty()))
                .setAllocatedQty(BigDecimal.ZERO)
                .setTransferInQty(BigDecimal.ZERO)
                .setTransferOutQty(BigDecimal.ZERO)
                .setRemark(createReqVO.getRemark());
        recalculateMetrics(saleOrder);
        saleOrderMapper.insert(saleOrder);
        LogRecordContext.putVariable(RdLogRecordConstants.BIZ_ID_VARIABLE, saleOrder.getId());
        return saleOrder.getId();
    }

    @Override
    @LogRecord(type = RdLogRecordConstants.SALE_ORDER_TYPE,
            subType = RdLogRecordConstants.SALE_ORDER_UPDATE_SUB_TYPE,
            bizNo = "{{#updateReqVO.id}}", success = RdLogRecordConstants.SALE_ORDER_UPDATE_SUCCESS)
    public void updateSaleOrder(RdSaleOrderSaveReqVO updateReqVO) {
        RdSaleOrderDO saleOrder = validateSaleOrderEditable(updateReqVO.getId());
        validateOrderNoUnique(updateReqVO.getId(), updateReqVO.getOrderNo());
        saleOrder.setOrderNo(updateReqVO.getOrderNo())
                .setOrderDate(updateReqVO.getOrderDate())
                .setCustomerName(updateReqVO.getCustomerName())
                .setProductModel(updateReqVO.getProductModel())
                .setRequiredQty(updateReqVO.getRequiredQty())
                .setActualOutputQty(zeroIfNull(updateReqVO.getActualOutputQty()))
                .setRemark(updateReqVO.getRemark());
        recalculateMetrics(saleOrder);
        saleOrderMapper.updateById(saleOrder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = RdLogRecordConstants.SALE_ORDER_TYPE,
            subType = RdLogRecordConstants.SALE_ORDER_CANCEL_SUB_TYPE,
            bizNo = "{{#id}}", success = RdLogRecordConstants.SALE_ORDER_CANCEL_SUCCESS)
    public void cancelSaleOrder(Long id) {
        RdSaleOrderDO saleOrder = getSaleOrderForUpdate(id);
        if (zeroIfNull(fulfillmentMapper.sumOutboundQtyByOrderId(id)).signum() > 0) {
            throw exception(SALE_ORDER_OUTBOUND_EXISTS);
        }
        saleOrderMapper.updateById(new RdSaleOrderDO()
                .setId(saleOrder.getId())
                .setStatus(RdOrderStatusEnum.CANCELLED.getStatus()));
    }

    @Override
    @LogRecord(type = RdLogRecordConstants.SALE_ORDER_TYPE,
            subType = RdLogRecordConstants.SALE_ORDER_RECALCULATE_SUB_TYPE,
            bizNo = "{{#id}}", success = RdLogRecordConstants.SALE_ORDER_RECALCULATE_SUCCESS)
    public void recalculateSaleOrder(Long id) {
        RdSaleOrderDO saleOrder = validateSaleOrderEditable(id);
        recalculateMetrics(saleOrder);
        saleOrderMapper.updateById(saleOrder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RdSaleOrderDO changeAllocatedQty(Long id, BigDecimal deltaQty) {
        RdSaleOrderDO saleOrder = saleOrderMapper.selectByIdForUpdate(id);
        if (saleOrder == null) {
            throw exception(SALE_ORDER_NOT_EXISTS);
        }
        validateSaleOrderEditable(saleOrder);
        if (deltaQty.signum() > 0
                && deltaQty.compareTo(zeroIfNull(saleOrder.getAdjustedDifferenceQty())) > 0) {
            throw exception(SALE_ORDER_ALLOCATION_EXCEEDS_DIFFERENCE);
        }
        BigDecimal allocatedQty = zeroIfNull(saleOrder.getAllocatedQty()).add(deltaQty);
        if (allocatedQty.signum() < 0) {
            throw exception(SALE_ORDER_ALLOCATION_NEGATIVE);
        }
        saleOrder.setAllocatedQty(allocatedQty);
        recalculateMetrics(saleOrder);
        saleOrderMapper.updateById(saleOrder);
        return saleOrder;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeTransferQty(Long fromOrderId, Long toOrderId, BigDecimal deltaQty) {
        RdSaleOrderDO fromOrder = null;
        RdSaleOrderDO toOrder;
        if (fromOrderId == null) {
            toOrder = getSaleOrderForUpdate(toOrderId);
        } else if (fromOrderId < toOrderId) {
            fromOrder = getSaleOrderForUpdate(fromOrderId);
            toOrder = getSaleOrderForUpdate(toOrderId);
        } else {
            toOrder = getSaleOrderForUpdate(toOrderId);
            fromOrder = getSaleOrderForUpdate(fromOrderId);
        }

        if (fromOrder != null) {
            BigDecimal transferOutQty = zeroIfNull(fromOrder.getTransferOutQty()).add(deltaQty);
            if (transferOutQty.signum() < 0) {
                throw exception(SALE_ORDER_TRANSFER_NEGATIVE);
            }
            fromOrder.setTransferOutQty(transferOutQty);
            recalculateMetrics(fromOrder);
            saleOrderMapper.updateById(fromOrder);
        }

        if (deltaQty.signum() > 0
                && deltaQty.compareTo(zeroIfNull(toOrder.getAdjustedDifferenceQty())) > 0) {
            throw exception(SALE_ORDER_TRANSFER_EXCEEDS_DIFFERENCE);
        }
        BigDecimal transferInQty = zeroIfNull(toOrder.getTransferInQty()).add(deltaQty);
        if (transferInQty.signum() < 0) {
            throw exception(SALE_ORDER_TRANSFER_NEGATIVE);
        }
        toOrder.setTransferInQty(transferInQty);
        recalculateMetrics(toOrder);
        saleOrderMapper.updateById(toOrder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RdSaleOrderDO lockSaleOrderForOutbound(Long id, boolean allowClosed) {
        RdSaleOrderDO saleOrder = saleOrderMapper.selectByIdForUpdate(id);
        if (saleOrder == null) {
            throw exception(SALE_ORDER_NOT_EXISTS);
        }
        if (RdOrderStatusEnum.CANCELLED.getStatus().equals(saleOrder.getStatus())
                || (!allowClosed && RdOrderStatusEnum.CLOSED.getStatus().equals(saleOrder.getStatus()))) {
            throw exception(SALE_ORDER_STATUS_CANNOT_CHANGE);
        }
        return saleOrder;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncOutboundStatus(Long id, BigDecimal totalOutboundQty, String operatorName) {
        RdSaleOrderDO saleOrder = saleOrderMapper.selectByIdForUpdate(id);
        if (saleOrder == null) {
            throw exception(SALE_ORDER_NOT_EXISTS);
        }
        if (RdOrderStatusEnum.CANCELLED.getStatus().equals(saleOrder.getStatus())) {
            throw exception(SALE_ORDER_STATUS_CANNOT_CHANGE);
        }
        boolean shouldClose = zeroIfNull(saleOrder.getFulfilledQty()).compareTo(saleOrder.getRequiredQty()) >= 0
                && zeroIfNull(totalOutboundQty).compareTo(saleOrder.getRequiredQty()) >= 0;
        if (shouldClose && !RdOrderStatusEnum.CLOSED.getStatus().equals(saleOrder.getStatus())) {
            saleOrderMapper.updateById(new RdSaleOrderDO()
                    .setId(id)
                    .setStatus(RdOrderStatusEnum.CLOSED.getStatus())
                    .setCloseTime(LocalDateTime.now())
                    .setCloseBy(operatorName));
        } else if (!shouldClose && RdOrderStatusEnum.CLOSED.getStatus().equals(saleOrder.getStatus())) {
            saleOrderMapper.reopenAfterOutboundCancel(id,
                    resolveStatus(saleOrder.getRequiredQty(), zeroIfNull(saleOrder.getFulfilledQty())),
                    operatorName == null ? "" : operatorName);
        }
    }

    @Override
    public RdSaleOrderDO getSaleOrder(Long id) {
        RdSaleOrderDO saleOrder = saleOrderMapper.selectById(id);
        if (saleOrder == null) {
            throw exception(SALE_ORDER_NOT_EXISTS);
        }
        return saleOrder;
    }

    @Override
    public Map<Long, RdSaleOrderDO> getSaleOrderMap(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> validIds = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (validIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return convertMap(saleOrderMapper.selectByIds(validIds), RdSaleOrderDO::getId);
    }

    @Override
    public PageResult<RdSaleOrderDO> getSaleOrderPage(RdSaleOrderPageReqVO pageReqVO) {
        return saleOrderMapper.selectPage(pageReqVO);
    }

    private RdSaleOrderDO validateSaleOrderEditable(Long id) {
        RdSaleOrderDO saleOrder = getSaleOrder(id);
        validateSaleOrderEditable(saleOrder);
        return saleOrder;
    }

    private RdSaleOrderDO getSaleOrderForUpdate(Long id) {
        RdSaleOrderDO saleOrder = saleOrderMapper.selectByIdForUpdate(id);
        if (saleOrder == null) {
            throw exception(SALE_ORDER_NOT_EXISTS);
        }
        validateSaleOrderEditable(saleOrder);
        return saleOrder;
    }

    private static void validateSaleOrderEditable(RdSaleOrderDO saleOrder) {
        if (RdOrderStatusEnum.CLOSED.getStatus().equals(saleOrder.getStatus())
                || RdOrderStatusEnum.CANCELLED.getStatus().equals(saleOrder.getStatus())) {
            throw exception(SALE_ORDER_STATUS_CANNOT_CHANGE);
        }
    }

    private void validateOrderNoUnique(Long id, String orderNo) {
        RdSaleOrderDO existingOrder = saleOrderMapper.selectByOrderNo(orderNo);
        if (existingOrder != null && (id == null || !existingOrder.getId().equals(id))) {
            throw exception(SALE_ORDER_NO_EXISTS);
        }
    }

    private static void recalculateMetrics(RdSaleOrderDO saleOrder) {
        BigDecimal requiredQty = zeroIfNull(saleOrder.getRequiredQty());
        BigDecimal actualOutputQty = zeroIfNull(saleOrder.getActualOutputQty());
        BigDecimal allocatedQty = zeroIfNull(saleOrder.getAllocatedQty());
        BigDecimal transferInQty = zeroIfNull(saleOrder.getTransferInQty());
        BigDecimal transferOutQty = zeroIfNull(saleOrder.getTransferOutQty());
        BigDecimal fulfilledQty = actualOutputQty.add(allocatedQty).add(transferInQty).subtract(transferOutQty);
        if (fulfilledQty.signum() < 0) {
            fulfilledQty = BigDecimal.ZERO;
        }

        saleOrder.setActualOutputQty(actualOutputQty)
                .setAllocatedQty(allocatedQty)
                .setTransferInQty(transferInQty)
                .setTransferOutQty(transferOutQty)
                .setDifferenceQty(requiredQty.subtract(actualOutputQty))
                .setAdjustedDifferenceQty(requiredQty.subtract(fulfilledQty))
                .setFulfilledQty(fulfilledQty)
                .setStatus(resolveStatus(requiredQty, fulfilledQty));
    }

    private static String resolveStatus(BigDecimal requiredQty, BigDecimal fulfilledQty) {
        if (fulfilledQty.compareTo(requiredQty) >= 0) {
            return RdOrderStatusEnum.FULFILLED.getStatus();
        }
        if (fulfilledQty.signum() > 0) {
            return RdOrderStatusEnum.PARTIAL.getStatus();
        }
        return RdOrderStatusEnum.OPEN.getStatus();
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

}
