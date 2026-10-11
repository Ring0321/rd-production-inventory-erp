package com.ruiding.erp.controller.admin.quality.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RdQualityInspectionPageReqVO extends PageParam {
    private String status;
    private String inspectionNo;
}
