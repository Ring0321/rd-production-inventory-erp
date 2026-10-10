package com.ruiding.erp.controller.admin.putaway.vo;

import com.ruiding.erp.dal.dataobject.putaway.RdPutawayTaskDO;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayTaskLineDO;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayExceptionDO;
import lombok.Data;
import lombok.experimental.Accessors;
import java.util.List;

@Data
@Accessors(chain = true)
public class RdPutawayBatchScanRespVO {
    private RdPutawayTaskDO task;
    private List<RdPutawayTaskLineDO> lines;
    private List<RdPutawayExceptionDO> exceptions;
    private String representativePackageCode;
    private Long recordId;
    private Boolean replayed;
    private Integer confirmedCount;
}
