package cn.iocoder.yudao.module.rd.service.dashboard;

import cn.iocoder.yudao.module.rd.controller.admin.dashboard.vo.RdInventoryDashboardRespVO;

import java.time.LocalDate;

public interface RdInventoryDashboardService {

    RdInventoryDashboardRespVO getOverview(LocalDate asOfDate);

}
