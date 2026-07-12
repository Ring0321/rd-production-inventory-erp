package com.ruiding.erp.service.stocktake;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.ruiding.erp.controller.admin.stocktake.vo.RdStocktakePageReqVO;
import com.ruiding.erp.controller.admin.stocktake.vo.RdStocktakeScanReqVO;
import com.ruiding.erp.dal.dataobject.stocktake.RdStocktakeOrderDO;
import com.ruiding.erp.dal.dataobject.stocktake.RdStocktakeOrderLineDO;

import java.time.LocalDate;
import java.util.List;

public interface RdStocktakeOrderService {
    Long create(Long locationId, LocalDate stocktakeDate, String remark);
    void freeze(Long id, Integer version);
    RdStocktakeOrderLineDO scan(RdStocktakeScanReqVO reqVO, Long operatorId, String operatorName);
    void submit(Long id, Integer version, Long operatorId, String operatorName);
    void approve(Long id, Integer version, Long reviewerId, String reviewerName);
    void adjust(Long id, Integer version, Long operatorId, String operatorName);
    void close(Long id, Integer version, Long operatorId, String operatorName);
    void cancel(Long id, Integer version, Long operatorId, String operatorName);
    RdStocktakeOrderDO get(Long id);
    List<RdStocktakeOrderLineDO> getLines(Long id);
    List<RdStocktakeOrderLineDO> getDifference(Long id);
    PageResult<RdStocktakeOrderDO> page(RdStocktakePageReqVO reqVO);
}
