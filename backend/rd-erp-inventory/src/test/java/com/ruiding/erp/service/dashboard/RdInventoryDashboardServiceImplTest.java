package com.ruiding.erp.service.dashboard;

import com.ruiding.erp.controller.admin.dashboard.vo.RdInventoryDashboardRespVO.MovementMetric;
import com.ruiding.erp.controller.admin.dashboard.vo.RdInventoryDashboardRespVO.OrderMetric;
import com.ruiding.erp.controller.admin.dashboard.vo.RdInventoryDashboardRespVO.StockMetric;
import com.ruiding.erp.dal.mysql.dashboard.RdInventoryDashboardMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RdInventoryDashboardServiceImplTest {

    @Mock
    private RdInventoryDashboardMapper dashboardMapper;
    @InjectMocks
    private RdInventoryDashboardServiceImpl service;

    @Test
    void shouldLoadFixedSizeDashboardWindow() {
        LocalDate asOfDate = LocalDate.of(2026, 7, 8);
        when(dashboardMapper.selectStockMetric()).thenReturn(new StockMetric());
        when(dashboardMapper.selectOpenOrderMetric()).thenReturn(new OrderMetric());
        when(dashboardMapper.selectMovementMetric(asOfDate)).thenReturn(new MovementMetric());
        when(dashboardMapper.selectTrend(LocalDate.of(2026, 6, 9), asOfDate))
                .thenReturn(Collections.emptyList());
        when(dashboardMapper.selectCustomerRanking()).thenReturn(Collections.emptyList());
        when(dashboardMapper.selectModelRanking()).thenReturn(Collections.emptyList());
        when(dashboardMapper.selectOrderStatuses()).thenReturn(Collections.emptyList());
        when(dashboardMapper.selectOpenOrders()).thenReturn(Collections.emptyList());

        assertEquals(asOfDate, service.getOverview(asOfDate).getAsOfDate());
        verify(dashboardMapper).selectTrend(LocalDate.of(2026, 6, 9), asOfDate);
    }

}
