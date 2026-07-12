package com.ruiding.erp.controller.admin.inbound.vo;

import com.ruiding.erp.dal.dataobject.inbound.RdInboundOrderDO;
import com.ruiding.erp.dal.dataobject.inbound.RdInboundOrderLineDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class RdInboundOrderDetailRespVO extends RdInboundOrderDO {
    private List<RdInboundOrderLineDO> lines;
}
