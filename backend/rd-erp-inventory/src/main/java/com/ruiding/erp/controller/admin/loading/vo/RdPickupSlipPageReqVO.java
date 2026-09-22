package com.ruiding.erp.controller.admin.loading.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RdPickupSlipPageReqVO extends PageParam {
    private String slipNo;
    private String customerName;
    private String status;
}
