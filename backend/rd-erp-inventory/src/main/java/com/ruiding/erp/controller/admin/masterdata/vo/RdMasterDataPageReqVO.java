package com.ruiding.erp.controller.admin.masterdata.vo;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data
@EqualsAndHashCode(callSuper = true)
public class RdMasterDataPageReqVO extends PageParam {
    private String keyword;
    private Boolean enabled;
}
