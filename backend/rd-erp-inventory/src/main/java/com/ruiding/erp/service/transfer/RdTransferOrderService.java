package com.ruiding.erp.service.transfer;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.ruiding.erp.controller.admin.transfer.vo.RdTransferOrderPageReqVO;
import com.ruiding.erp.controller.admin.transfer.vo.RdTransferOrderSaveReqVO;
import com.ruiding.erp.dal.dataobject.transfer.RdTransferOrderDO;

import javax.validation.Valid;

/**
 * RD transfer order Service.
 */
public interface RdTransferOrderService {

    Long createTransferOrder(@Valid RdTransferOrderSaveReqVO createReqVO);

    void updateTransferOrder(@Valid RdTransferOrderSaveReqVO updateReqVO);

    void confirmTransferOrder(Long id, Long operatorId, String operatorName);

    void cancelTransferOrder(Long id, Long operatorId, String operatorName);

    RdTransferOrderDO getTransferOrder(Long id);

    PageResult<RdTransferOrderDO> getTransferOrderPage(RdTransferOrderPageReqVO pageReqVO);

}
