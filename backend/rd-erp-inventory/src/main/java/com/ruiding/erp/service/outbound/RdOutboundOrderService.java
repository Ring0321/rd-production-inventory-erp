package com.ruiding.erp.service.outbound;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.ruiding.erp.controller.admin.outbound.vo.RdOutboundOrderPageReqVO;
import com.ruiding.erp.controller.admin.outbound.vo.RdOutboundOrderSaveReqVO;
import com.ruiding.erp.dal.dataobject.outbound.RdOutboundOrderDO;

import javax.validation.Valid;

public interface RdOutboundOrderService {

    Long createOutboundOrder(@Valid RdOutboundOrderSaveReqVO createReqVO);

    void updateOutboundOrder(@Valid RdOutboundOrderSaveReqVO updateReqVO);

    void confirmOutboundOrder(Long id, Long operatorId, String operatorName);

    void cancelOutboundOrder(Long id, Long operatorId, String operatorName);

    RdOutboundOrderDO getOutboundOrder(Long id);

    PageResult<RdOutboundOrderDO> getOutboundOrderPage(RdOutboundOrderPageReqVO pageReqVO);

}
