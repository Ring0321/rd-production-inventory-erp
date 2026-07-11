package com.ruiding.erp.service.stock;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.ruiding.erp.controller.admin.stock.vo.RdStockTransactionPageReqVO;
import com.ruiding.erp.dal.dataobject.stock.RdStockTransactionDO;
import com.ruiding.erp.dal.mysql.stock.RdStockTransactionMapper;
import com.ruiding.erp.service.report.RdDailyStockSummaryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.ruiding.erp.enums.ErrorCodeConstants.STOCK_TRANSACTION_NOT_EXISTS;

/**
 * RD stock transaction Service implementation.
 */
@Service
@Validated
public class RdStockTransactionServiceImpl implements RdStockTransactionService {

    @Resource
    private RdStockTransactionMapper stockTransactionMapper;
    @Resource
    private RdDailyStockSummaryService dailyStockSummaryService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createStockTransaction(RdStockTransactionDO stockTransaction) {
        stockTransactionMapper.insert(stockTransaction);
        dailyStockSummaryService.applyStockTransaction(stockTransaction);
        return stockTransaction.getId();
    }

    @Override
    public RdStockTransactionDO getStockTransaction(Long id) {
        RdStockTransactionDO stockTransaction = stockTransactionMapper.selectById(id);
        if (stockTransaction == null) {
            throw exception(STOCK_TRANSACTION_NOT_EXISTS);
        }
        return stockTransaction;
    }

    @Override
    public PageResult<RdStockTransactionDO> getStockTransactionPage(RdStockTransactionPageReqVO pageReqVO) {
        return stockTransactionMapper.selectPage(pageReqVO);
    }

}
