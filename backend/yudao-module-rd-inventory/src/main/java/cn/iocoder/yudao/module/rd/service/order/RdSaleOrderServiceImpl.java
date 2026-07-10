package cn.iocoder.yudao.module.rd.service.order;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rd.controller.admin.order.vo.RdSaleOrderPageReqVO;
import cn.iocoder.yudao.module.rd.controller.admin.order.vo.RdSaleOrderSaveReqVO;
import cn.iocoder.yudao.module.rd.dal.dataobject.order.RdSaleOrderDO;
import cn.iocoder.yudao.module.rd.dal.mysql.order.RdSaleOrderMapper;
import cn.iocoder.yudao.module.rd.enums.RdOrderStatusEnum;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.math.BigDecimal;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.SALE_ORDER_NOT_EXISTS;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.SALE_ORDER_NO_EXISTS;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.SALE_ORDER_STATUS_CANNOT_CHANGE;

/**
 * RD sales order Service implementation.
 */
@Service
@Validated
public class RdSaleOrderServiceImpl implements RdSaleOrderService {

    @Resource
    private RdSaleOrderMapper saleOrderMapper;

    @Override
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
        return saleOrder.getId();
    }

    @Override
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
    public void cancelSaleOrder(Long id) {
        RdSaleOrderDO saleOrder = validateSaleOrderEditable(id);
        saleOrderMapper.updateById(new RdSaleOrderDO()
                .setId(saleOrder.getId())
                .setStatus(RdOrderStatusEnum.CANCELLED.getStatus()));
    }

    @Override
    public void recalculateSaleOrder(Long id) {
        RdSaleOrderDO saleOrder = validateSaleOrderEditable(id);
        recalculateMetrics(saleOrder);
        saleOrderMapper.updateById(saleOrder);
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
    public PageResult<RdSaleOrderDO> getSaleOrderPage(RdSaleOrderPageReqVO pageReqVO) {
        return saleOrderMapper.selectPage(pageReqVO);
    }

    private RdSaleOrderDO validateSaleOrderEditable(Long id) {
        RdSaleOrderDO saleOrder = getSaleOrder(id);
        if (RdOrderStatusEnum.CLOSED.getStatus().equals(saleOrder.getStatus())
                || RdOrderStatusEnum.CANCELLED.getStatus().equals(saleOrder.getStatus())) {
            throw exception(SALE_ORDER_STATUS_CANNOT_CHANGE);
        }
        return saleOrder;
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
