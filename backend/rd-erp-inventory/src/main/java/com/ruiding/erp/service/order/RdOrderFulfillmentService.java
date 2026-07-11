package com.ruiding.erp.service.order;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.ruiding.erp.controller.admin.order.vo.RdOrderFulfillmentAllocateReqVO;
import com.ruiding.erp.controller.admin.order.vo.RdOrderFulfillmentPageReqVO;
import com.ruiding.erp.controller.admin.order.vo.RdOrderStockCandidatePageReqVO;
import com.ruiding.erp.dal.dataobject.order.RdOrderFulfillmentDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockLotDO;

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
