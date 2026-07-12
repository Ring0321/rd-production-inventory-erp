package com.ruiding.erp.controller.admin.outbound.vo;

import com.ruiding.erp.dal.dataobject.outbound.RdOutboundOrderLineDO;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class RdOutboundOrderDetailRespVO {
    private RdOutboundOrderRespVO order;
    private List<RdOutboundOrderLineDO> lines;
}
