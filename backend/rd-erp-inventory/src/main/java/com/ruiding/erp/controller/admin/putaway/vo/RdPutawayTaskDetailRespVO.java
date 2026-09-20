package com.ruiding.erp.controller.admin.putaway.vo;

import com.ruiding.erp.dal.dataobject.putaway.RdPutawayExceptionDO;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayTaskDO;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayTaskLineDO;
import lombok.Data;

import java.util.List;

@Data
public class RdPutawayTaskDetailRespVO {
    private RdPutawayTaskDO task;
    private List<RdPutawayTaskLineDO> lines;
    private List<RdPutawayExceptionDO> exceptions;
}
