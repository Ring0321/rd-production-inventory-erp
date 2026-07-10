package cn.iocoder.yudao.module.rd.service.outbound;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rd.controller.admin.outbound.vo.RdOutboundOrderPageReqVO;
import cn.iocoder.yudao.module.rd.controller.admin.outbound.vo.RdOutboundOrderSaveReqVO;
import cn.iocoder.yudao.module.rd.dal.dataobject.outbound.RdOutboundOrderDO;

import javax.validation.Valid;

public interface RdOutboundOrderService {

    Long createOutboundOrder(@Valid RdOutboundOrderSaveReqVO createReqVO);

    void updateOutboundOrder(@Valid RdOutboundOrderSaveReqVO updateReqVO);

    void confirmOutboundOrder(Long id, Long operatorId, String operatorName);

    void cancelOutboundOrder(Long id, Long operatorId, String operatorName);

    RdOutboundOrderDO getOutboundOrder(Long id);

    PageResult<RdOutboundOrderDO> getOutboundOrderPage(RdOutboundOrderPageReqVO pageReqVO);

}
