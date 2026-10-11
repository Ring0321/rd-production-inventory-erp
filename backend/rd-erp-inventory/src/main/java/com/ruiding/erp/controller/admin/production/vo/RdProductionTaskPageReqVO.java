package com.ruiding.erp.controller.admin.production.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RdProductionTaskPageReqVO extends PageParam {
    private String taskNo;
    private String customerName;
    private String status;
}
