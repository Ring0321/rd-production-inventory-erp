package com.ruiding.erp.service.report;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.ruiding.erp.controller.admin.report.vo.RdDailyStockSummaryPageReqVO;
import com.ruiding.erp.controller.admin.report.vo.RdDailyStockTrendRespVO;
import com.ruiding.erp.dal.dataobject.report.RdDailyStockSummaryDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockTransactionDO;

import javax.validation.Valid;
import java.time.LocalDate;
import java.util.List;

public interface RdDailyStockSummaryService {

    void applyStockTransaction(RdStockTransactionDO stockTransaction);

    int rebuild(LocalDate startDate, LocalDate endDate);

    PageResult<RdDailyStockSummaryDO> getPage(@Valid RdDailyStockSummaryPageReqVO pageReqVO);

    List<RdDailyStockTrendRespVO> getTrend(@Valid RdDailyStockSummaryPageReqVO reqVO);

}
