package cn.iocoder.yudao.module.rd.service.production;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rd.controller.admin.production.vo.RdProductionOutputPageReqVO;
import cn.iocoder.yudao.module.rd.controller.admin.production.vo.RdProductionOutputSaveReqVO;
import cn.iocoder.yudao.module.rd.dal.dataobject.production.RdProductionOutputDO;

import javax.validation.Valid;

/**
 * RD furnace-layer production output Service.
 */
public interface RdProductionOutputService {

    Long createProductionOutput(@Valid RdProductionOutputSaveReqVO createReqVO);

    void updateProductionOutput(@Valid RdProductionOutputSaveReqVO updateReqVO);

    void cancelProductionOutput(Long id);

    void inboundProductionOutput(Long id);

    RdProductionOutputDO getProductionOutput(Long id);

    PageResult<RdProductionOutputDO> getProductionOutputPage(RdProductionOutputPageReqVO pageReqVO);

}
