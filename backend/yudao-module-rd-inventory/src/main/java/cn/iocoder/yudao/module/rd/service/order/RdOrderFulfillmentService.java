package cn.iocoder.yudao.module.rd.service.order;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rd.controller.admin.order.vo.RdOrderFulfillmentAllocateReqVO;
import cn.iocoder.yudao.module.rd.controller.admin.order.vo.RdOrderFulfillmentPageReqVO;
import cn.iocoder.yudao.module.rd.controller.admin.order.vo.RdOrderStockCandidatePageReqVO;
import cn.iocoder.yudao.module.rd.dal.dataobject.order.RdOrderFulfillmentDO;
import cn.iocoder.yudao.module.rd.dal.dataobject.stock.RdStockLotDO;

import javax.validation.Valid;
import java.math.BigDecimal;

/**
 * RD order fulfillment Service.
 */
public interface RdOrderFulfillmentService {

    Long allocateStock(@Valid RdOrderFulfillmentAllocateReqVO allocateReqVO);

    BigDecimal autoAllocateStock(Long orderId);

    void cancelAllocation(Long id);

    PageResult<RdStockLotDO> getCandidateStockLotPage(@Valid RdOrderStockCandidatePageReqVO pageReqVO);

    PageResult<RdOrderFulfillmentDO> getFulfillmentPage(RdOrderFulfillmentPageReqVO pageReqVO);

}
