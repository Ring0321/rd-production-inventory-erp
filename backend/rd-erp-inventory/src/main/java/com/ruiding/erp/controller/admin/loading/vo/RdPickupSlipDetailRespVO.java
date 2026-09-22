package com.ruiding.erp.controller.admin.loading.vo;

import com.ruiding.erp.dal.dataobject.loading.RdPickupSlipDO;
import com.ruiding.erp.dal.dataobject.loading.RdPickupSlipLineDO;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class RdPickupSlipDetailRespVO {
    private RdPickupSlipDO slip;
    private List<RdPickupSlipLineDO> lines;
}
