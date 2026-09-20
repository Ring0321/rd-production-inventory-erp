package com.ruiding.erp.controller.admin.putaway.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RdPutawayTaskPageReqVO extends PageParam {
    private String taskNo;
    private String status;
    private Long plannedLocationId;
    private Long forkliftDriverId;
}
