package com.ruiding.erp.controller.admin.loading.vo;

import com.ruiding.erp.dal.dataobject.loading.RdLoadingTaskDO;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingTaskLineDO;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class RdLoadingTaskDetailRespVO {
    private RdLoadingTaskDO task;
    private List<RdLoadingTaskLineDO> lines;
}
