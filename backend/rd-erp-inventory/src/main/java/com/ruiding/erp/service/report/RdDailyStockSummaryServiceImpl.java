package com.ruiding.erp.service.report;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.ruiding.erp.controller.admin.report.vo.RdDailyStockSummaryPageReqVO;
import com.ruiding.erp.controller.admin.report.vo.RdDailyStockTrendRespVO;
import com.ruiding.erp.dal.dataobject.report.RdDailyStockSummaryDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockTransactionDO;
import com.ruiding.erp.dal.mysql.report.RdDailyStockSummaryMapper;
import com.ruiding.erp.enums.RdLogRecordConstants;
import com.mzt.logapi.starter.annotation.LogRecord;
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
    @LogRecord(type = RdLogRecordConstants.DAILY_SUMMARY_TYPE,
            subType = RdLogRecordConstants.DAILY_SUMMARY_REBUILD_SUB_TYPE,
            bizNo = "0", success = RdLogRecordConstants.DAILY_SUMMARY_REBUILD_SUCCESS)
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
