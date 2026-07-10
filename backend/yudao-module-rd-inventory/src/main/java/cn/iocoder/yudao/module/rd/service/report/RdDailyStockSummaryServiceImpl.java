package cn.iocoder.yudao.module.rd.service.report;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rd.controller.admin.report.vo.RdDailyStockSummaryPageReqVO;
import cn.iocoder.yudao.module.rd.controller.admin.report.vo.RdDailyStockTrendRespVO;
import cn.iocoder.yudao.module.rd.dal.dataobject.report.RdDailyStockSummaryDO;
import cn.iocoder.yudao.module.rd.dal.dataobject.stock.RdStockTransactionDO;
import cn.iocoder.yudao.module.rd.dal.mysql.report.RdDailyStockSummaryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@Validated
public class RdDailyStockSummaryServiceImpl implements RdDailyStockSummaryService {

    @Resource
    private RdDailyStockSummaryMapper dailyStockSummaryMapper;

    @Override
    public void applyStockTransaction(RdStockTransactionDO stockTransaction) {
        if (stockTransaction.getStockLotId() == null) {
            return;
        }
        RdDailyStockSummaryCalculator.Delta delta = RdDailyStockSummaryCalculator.calculate(
                stockTransaction.getTransactionType(), stockTransaction.getQuantity());
        BigDecimal packageWeight = zeroIfNull(stockTransaction.getPackageWeight());
        BigDecimal warehouseQty = zeroIfNull(stockTransaction.getAfterQty());
        RdDailyStockSummaryDO summary = new RdDailyStockSummaryDO()
                .setBizDate(stockTransaction.getBizDate())
                .setStockLotId(stockTransaction.getStockLotId())
                .setLocationCode(stockTransaction.getLocationCode() == null ? "" : stockTransaction.getLocationCode())
                .setCustomerName(stockTransaction.getCustomerName())
                .setProductModel(stockTransaction.getProductModel())
                .setFurnaceNo(stockTransaction.getFurnaceNo())
                .setFurnaceLayer(stockTransaction.getFurnaceLayer())
                .setProductionQty(delta.getProductionQty())
                .setInboundQty(delta.getInboundQty())
                .setOutboundQty(delta.getOutboundQty())
                .setTransferInQty(delta.getTransferInQty())
                .setTransferOutQty(delta.getTransferOutQty())
                .setReturnQty(delta.getReturnQty())
                .setNetChangeQty(delta.getNetChangeQty())
                .setWarehouseQty(warehouseQty)
                .setStockWeight(warehouseQty.multiply(packageWeight));
        dailyStockSummaryMapper.upsertIncrement(summary);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int rebuild(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Invalid daily summary date range");
        }
        List<RdDailyStockSummaryDO> summaries = dailyStockSummaryMapper.selectDailyAggregates(startDate, endDate);
        for (RdDailyStockSummaryDO summary : summaries) {
            summary.setLocationCode(summary.getLocationCode() == null ? "" : summary.getLocationCode());
            dailyStockSummaryMapper.upsertRebuilt(summary);
        }
        return summaries.size();
    }

    @Override
    public PageResult<RdDailyStockSummaryDO> getPage(RdDailyStockSummaryPageReqVO pageReqVO) {
        return dailyStockSummaryMapper.selectPage(pageReqVO);
    }

    @Override
    public List<RdDailyStockTrendRespVO> getTrend(RdDailyStockSummaryPageReqVO reqVO) {
        return dailyStockSummaryMapper.selectTrend(reqVO);
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

}
