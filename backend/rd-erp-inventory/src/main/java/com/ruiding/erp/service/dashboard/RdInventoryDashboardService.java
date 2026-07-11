package com.ruiding.erp.service.dashboard;

import com.ruiding.erp.controller.admin.dashboard.vo.RdInventoryDashboardRespVO;

import java.time.LocalDate;

public interface RdInventoryDashboardService {

    RdInventoryDashboardRespVO getOverview(LocalDate asOfDate);

}
