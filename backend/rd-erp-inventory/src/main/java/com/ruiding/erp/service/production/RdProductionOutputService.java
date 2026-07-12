package com.ruiding.erp.service.production;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.ruiding.erp.controller.admin.production.vo.RdProductionOutputPageReqVO;
import com.ruiding.erp.controller.admin.production.vo.RdProductionOutputSaveReqVO;
import com.ruiding.erp.dal.dataobject.production.RdProductionOutputDO;

import javax.validation.Valid;

/**
 * RD furnace-layer production output Service.
 */
public interface RdProductionOutputService {

    Long createProductionOutput(@Valid RdProductionOutputSaveReqVO createReqVO);

    void updateProductionOutput(@Valid RdProductionOutputSaveReqVO updateReqVO);

    void cancelProductionOutput(Long id, Integer version);

    void confirmProductionOutput(Long id, Integer version);

    void inboundProductionOutput(Long id);

    RdProductionOutputDO getProductionOutput(Long id);

    PageResult<RdProductionOutputDO> getProductionOutputPage(RdProductionOutputPageReqVO pageReqVO);

}
