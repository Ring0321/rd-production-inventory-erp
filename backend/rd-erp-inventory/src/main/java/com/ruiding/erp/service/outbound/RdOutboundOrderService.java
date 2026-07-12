package com.ruiding.erp.service.outbound;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.ruiding.erp.controller.admin.outbound.vo.RdOutboundOrderPageReqVO;
import com.ruiding.erp.controller.admin.outbound.vo.RdOutboundOrderSaveReqVO;
import com.ruiding.erp.controller.admin.outbound.vo.RdOutboundScanReqVO;
import com.ruiding.erp.dal.dataobject.outbound.RdOutboundOrderDO;
import com.ruiding.erp.dal.dataobject.outbound.RdOutboundOrderLineDO;

import javax.validation.Valid;
import java.util.List;

public interface RdOutboundOrderService {

    Long createOutboundOrder(@Valid RdOutboundOrderSaveReqVO createReqVO);

    void updateOutboundOrder(@Valid RdOutboundOrderSaveReqVO updateReqVO);

    void confirmOutboundOrder(Long id, Long operatorId, String operatorName);

    void cancelOutboundOrder(Long id, Long operatorId, String operatorName);

    RdOutboundOrderLineDO scanPackage(@Valid RdOutboundScanReqVO reqVO, Long operatorId, String operatorName);

    void removeScannedPackage(Long id, Long packageId);

    void submitOutboundOrder(Long id, Integer version, Long operatorId, String operatorName);

    void approveOutboundOrder(Long id, Integer version, Long reviewerId, String reviewerName);

    void postOutboundOrder(Long id, Integer version, Long operatorId, String operatorName);

    void reverseOutboundOrder(Long id, Integer version, Long operatorId, String operatorName, String reason);

    List<RdOutboundOrderLineDO> getOutboundOrderLines(Long id);

    RdOutboundOrderDO getOutboundOrder(Long id);

    PageResult<RdOutboundOrderDO> getOutboundOrderPage(RdOutboundOrderPageReqVO pageReqVO);

}
