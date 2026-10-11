package com.ruiding.erp.controller.admin.quality.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import javax.validation.constraints.NotBlank;

@Data
@EqualsAndHashCode(callSuper = true)
public class RdQualityInspectionScopeReqVO extends PageParam {
    @NotBlank private String packageCode;
}
