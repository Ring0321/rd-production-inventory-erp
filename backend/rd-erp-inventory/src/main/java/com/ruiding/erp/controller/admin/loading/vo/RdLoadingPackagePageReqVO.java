package com.ruiding.erp.controller.admin.loading.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RdLoadingPackagePageReqVO extends PageParam {
    private String customerName;
    private Long saleOrderId;
    private String productModel;
    private String furnaceNo;
    private String furnaceLayer;
    private Long locationId;
    private String keyword;
}
