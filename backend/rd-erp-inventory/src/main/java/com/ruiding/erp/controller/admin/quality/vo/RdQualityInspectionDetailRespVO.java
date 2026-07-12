package com.ruiding.erp.controller.admin.quality.vo;

import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionDO;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionItemDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class RdQualityInspectionDetailRespVO extends RdQualityInspectionDO {
    private List<RdQualityInspectionItemDO> items;
}
