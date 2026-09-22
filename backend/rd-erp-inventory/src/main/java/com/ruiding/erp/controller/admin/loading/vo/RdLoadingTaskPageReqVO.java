package com.ruiding.erp.controller.admin.loading.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RdLoadingTaskPageReqVO extends PageParam {
    private String taskNo;
    private String pickupSlipNo;
    private String customerName;
    private String status;
    private Long forkliftDriverId;
}
