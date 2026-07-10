package cn.iocoder.yudao.module.rd.service.stock;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rd.controller.admin.stock.vo.RdStockAdjustmentCreateReqVO;
import cn.iocoder.yudao.module.rd.controller.admin.stock.vo.RdStockLotPageReqVO;
import cn.iocoder.yudao.module.rd.controller.admin.stock.vo.RdStockOpeningCreateReqVO;
import cn.iocoder.yudao.module.rd.dal.dataobject.production.RdProductionOutputDO;
import cn.iocoder.yudao.module.rd.dal.dataobject.stock.RdStockLotDO;

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
