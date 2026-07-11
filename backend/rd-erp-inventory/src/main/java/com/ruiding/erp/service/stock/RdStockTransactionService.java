package com.ruiding.erp.service.stock;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.ruiding.erp.controller.admin.stock.vo.RdStockTransactionPageReqVO;
import com.ruiding.erp.dal.dataobject.stock.RdStockTransactionDO;

import javax.validation.Valid;

/**
 * RD stock transaction Service.
 */
public interface RdStockTransactionService {

    Long createStockTransaction(RdStockTransactionDO stockTransaction);

    RdStockTransactionDO getStockTransaction(Long id);

    PageResult<RdStockTransactionDO> getStockTransactionPage(@Valid RdStockTransactionPageReqVO pageReqVO);

}
