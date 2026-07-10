package cn.iocoder.yudao.module.rd.service.order;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rd.controller.admin.order.vo.RdSaleOrderPageReqVO;
import cn.iocoder.yudao.module.rd.controller.admin.order.vo.RdSaleOrderSaveReqVO;
import cn.iocoder.yudao.module.rd.dal.dataobject.order.RdSaleOrderDO;

import javax.validation.Valid;
import java.math.BigDecimal;

/**
 * RD sales order Service.
 */
public interface RdSaleOrderService {

    Long createSaleOrder(@Valid RdSaleOrderSaveReqVO createReqVO);

    void updateSaleOrder(@Valid RdSaleOrderSaveReqVO updateReqVO);

    void cancelSaleOrder(Long id);

    void recalculateSaleOrder(Long id);

    RdSaleOrderDO changeAllocatedQty(Long id, BigDecimal deltaQty);

    RdSaleOrderDO getSaleOrder(Long id);

    PageResult<RdSaleOrderDO> getSaleOrderPage(RdSaleOrderPageReqVO pageReqVO);

}
