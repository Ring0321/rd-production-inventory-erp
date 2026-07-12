package com.ruiding.erp.controller.admin.ordersplit.vo;

import com.ruiding.erp.dal.dataobject.ordersplit.RdOrderSplitResultDO;
import com.ruiding.erp.dal.dataobject.ordersplit.RdOrderSplitResultLineDO;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class RdOrderSplitDetailRespVO {
    private RdOrderSplitResultDO result;
    private List<RdOrderSplitResultLineDO> lines;
    private List<RdOrderSplitOutputBindRespVO> bindings;
}
