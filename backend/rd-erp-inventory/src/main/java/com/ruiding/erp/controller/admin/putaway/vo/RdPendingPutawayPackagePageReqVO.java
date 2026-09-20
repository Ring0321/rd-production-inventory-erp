package com.ruiding.erp.controller.admin.putaway.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RdPendingPutawayPackagePageReqVO extends PageParam {
    private String keyword;
    private String customerName;
    private String productModel;
    private String furnaceNo;
    private String furnaceLayer;
}
