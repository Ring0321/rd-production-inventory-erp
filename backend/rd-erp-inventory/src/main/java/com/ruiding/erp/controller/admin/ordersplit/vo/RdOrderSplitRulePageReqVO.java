package com.ruiding.erp.controller.admin.ordersplit.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RdOrderSplitRulePageReqVO extends PageParam {
    private String ruleCode;
    private String ruleName;
    private String customerName;
    private String productModel;
    private Boolean enabled;
}
