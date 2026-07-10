package cn.iocoder.yudao.module.rd.service.dashboard;

import cn.iocoder.yudao.module.rd.controller.admin.dashboard.vo.RdInventoryDashboardRespVO;
import cn.iocoder.yudao.module.rd.dal.mysql.dashboard.RdInventoryDashboardMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;

@Service
public class RdInventoryDashboardServiceImpl implements RdInventoryDashboardService {

    private static final int TREND_DAYS = 30;

    @Resource
    private RdInventoryDashboardMapper dashboardMapper;

    @Override
    public RdInventoryDashboardRespVO getOverview(LocalDate asOfDate) {
        LocalDate targetDate = asOfDate == null ? LocalDate.now() : asOfDate;
        RdInventoryDashboardRespVO response = new RdInventoryDashboardRespVO();
        response.setAsOfDate(targetDate);
        response.setStock(dashboardMapper.selectStockMetric());
        response.setOrder(dashboardMapper.selectOpenOrderMetric());
        response.setTodayMovement(dashboardMapper.selectMovementMetric(targetDate));
        response.setTrend(dashboardMapper.selectTrend(targetDate.minusDays(TREND_DAYS - 1L), targetDate));
        response.setCustomerRanking(dashboardMapper.selectCustomerRanking());
        response.setModelRanking(dashboardMapper.selectModelRanking());
        response.setOrderStatuses(dashboardMapper.selectOrderStatuses());
        response.setOpenOrders(dashboardMapper.selectOpenOrders());
        return response;
    }

}
