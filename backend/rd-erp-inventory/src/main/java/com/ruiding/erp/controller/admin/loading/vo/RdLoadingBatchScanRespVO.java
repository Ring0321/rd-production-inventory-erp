package com.ruiding.erp.controller.admin.loading.vo;

import com.ruiding.erp.dal.dataobject.loading.RdLoadingTaskDO;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingTaskLineDO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class RdLoadingBatchScanRespVO {
    private Long recordId;
    private RdLoadingTaskDO task;
    private List<RdLoadingTaskLineDO> lines;
    private Long matchedTaskLineId;
    private String representativePackageCode;
    private String scanResult;
    private Boolean success;
    private Boolean replayed;
    private Integer confirmedCount;
    private Integer remainingCount;
    private BigDecimal confirmedWeight;
    private String message;
}
