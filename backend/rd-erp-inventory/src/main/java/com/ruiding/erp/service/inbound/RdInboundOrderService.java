package com.ruiding.erp.service.inbound;

import com.ruiding.erp.dal.dataobject.inbound.RdInboundOrderDO;
import com.ruiding.erp.dal.dataobject.inbound.RdInboundOrderLineDO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;

import java.time.LocalDate;
import java.util.List;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;

public interface RdInboundOrderService {
    Long create(LocalDate bizDate, Long productionOutputId, List<RdInboundOrderLineDO> lines, String remark);
    void submit(Long id, Long operatorId, String operatorName);
    void verify(Long id, Long reviewerId, String reviewerName);
    void post(Long id, Long operatorId, String operatorName);
    void reverse(Long id, Long operatorId, String operatorName, String reason);
    RdPackageUnitDO validatePackageForInbound(Long packageId);
    RdInboundOrderDO get(Long id);
    PageResult<RdInboundOrderDO> page(PageParam pageParam);
}
