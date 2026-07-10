package cn.iocoder.yudao.module.rd.service.stock;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rd.controller.admin.stock.vo.RdStockTransactionPageReqVO;
import cn.iocoder.yudao.module.rd.dal.dataobject.stock.RdStockTransactionDO;
import cn.iocoder.yudao.module.rd.dal.mysql.stock.RdStockTransactionMapper;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.STOCK_TRANSACTION_NOT_EXISTS;

/**
 * RD stock transaction Service implementation.
 */
@Service
@Validated
public class RdStockTransactionServiceImpl implements RdStockTransactionService {

    @Resource
    private RdStockTransactionMapper stockTransactionMapper;

    @Override
    public Long createStockTransaction(RdStockTransactionDO stockTransaction) {
        stockTransactionMapper.insert(stockTransaction);
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
