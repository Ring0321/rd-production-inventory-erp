package cn.iocoder.yudao.module.rd.service.stock;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rd.controller.admin.stock.vo.RdStockTransactionPageReqVO;
import cn.iocoder.yudao.module.rd.dal.dataobject.stock.RdStockTransactionDO;

import javax.validation.Valid;

/**
 * RD stock transaction Service.
 */
public interface RdStockTransactionService {

    RdStockTransactionDO getStockTransaction(Long id);

    PageResult<RdStockTransactionDO> getStockTransactionPage(@Valid RdStockTransactionPageReqVO pageReqVO);

}
