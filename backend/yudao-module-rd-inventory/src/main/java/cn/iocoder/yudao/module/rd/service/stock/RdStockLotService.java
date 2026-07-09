package cn.iocoder.yudao.module.rd.service.stock;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rd.controller.admin.stock.vo.RdStockLotPageReqVO;
import cn.iocoder.yudao.module.rd.dal.dataobject.production.RdProductionOutputDO;
import cn.iocoder.yudao.module.rd.dal.dataobject.stock.RdStockLotDO;

/**
 * RD stock lot Service.
 */
public interface RdStockLotService {

    RdStockLotDO createProductionInbound(RdProductionOutputDO productionOutput);

    RdStockLotDO getStockLot(Long id);

    PageResult<RdStockLotDO> getStockLotPage(RdStockLotPageReqVO pageReqVO);

}
