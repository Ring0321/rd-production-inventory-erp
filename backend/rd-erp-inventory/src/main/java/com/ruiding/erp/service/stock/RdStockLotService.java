package com.ruiding.erp.service.stock;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.ruiding.erp.controller.admin.stock.vo.RdStockAdjustmentCreateReqVO;
import com.ruiding.erp.controller.admin.stock.vo.RdStockLotPageReqVO;
import com.ruiding.erp.controller.admin.stock.vo.RdStockOpeningCreateReqVO;
import com.ruiding.erp.dal.dataobject.production.RdProductionOutputDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockLotDO;

import javax.validation.Valid;
import java.util.Collection;
import java.util.Map;

/**
 * RD stock lot Service.
 */
public interface RdStockLotService {

    RdStockLotDO createProductionInbound(RdProductionOutputDO productionOutput);

    Long createOpeningStock(@Valid RdStockOpeningCreateReqVO createReqVO,
                            Long operatorId, String operatorName);

    Long createImportedOpeningStock(@Valid RdStockOpeningCreateReqVO createReqVO,
                                    Long importTaskId, Integer excelRowNo,
                                    Long operatorId, String operatorName);

    Long adjustStock(@Valid RdStockAdjustmentCreateReqVO createReqVO,
                     Long operatorId, String operatorName);

    RdStockLotDO getStockLot(Long id);

    Map<Long, RdStockLotDO> getStockLotMap(Collection<Long> ids);

    PageResult<RdStockLotDO> getStockLotPage(RdStockLotPageReqVO pageReqVO);

}
