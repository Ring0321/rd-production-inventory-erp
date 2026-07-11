package com.ruiding.erp.job.report;

import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import com.ruiding.erp.service.report.RdDailyStockSummaryService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDate;

@Component
public class RdDailyStockSummaryJob implements JobHandler {

    @Resource
    private RdDailyStockSummaryService dailyStockSummaryService;

    @Override
    public String execute(String param) {
        LocalDate bizDate = param == null || param.trim().isEmpty()
                ? LocalDate.now().minusDays(1) : LocalDate.parse(param.trim());
        int count = dailyStockSummaryService.rebuild(bizDate, bizDate);
        return "Rebuilt " + count + " RD daily stock summary rows for " + bizDate;
    }

}
