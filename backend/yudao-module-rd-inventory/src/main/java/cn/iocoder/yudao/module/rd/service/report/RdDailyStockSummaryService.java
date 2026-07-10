package cn.iocoder.yudao.module.rd.service.report;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rd.controller.admin.report.vo.RdDailyStockSummaryPageReqVO;
import cn.iocoder.yudao.module.rd.controller.admin.report.vo.RdDailyStockTrendRespVO;
import cn.iocoder.yudao.module.rd.dal.dataobject.report.RdDailyStockSummaryDO;
import cn.iocoder.yudao.module.rd.dal.dataobject.stock.RdStockTransactionDO;

import javax.validation.Valid;
import java.time.LocalDate;
import java.util.List;

public interface RdDailyStockSummaryService {

    void applyStockTransaction(RdStockTransactionDO stockTransaction);

    int rebuild(LocalDate startDate, LocalDate endDate);

    PageResult<RdDailyStockSummaryDO> getPage(@Valid RdDailyStockSummaryPageReqVO pageReqVO);

    List<RdDailyStockTrendRespVO> getTrend(@Valid RdDailyStockSummaryPageReqVO reqVO);

}
