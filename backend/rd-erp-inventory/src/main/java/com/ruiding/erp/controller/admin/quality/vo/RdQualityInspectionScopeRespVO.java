package com.ruiding.erp.controller.admin.quality.vo;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
public class RdQualityInspectionScopeRespVO {
    private Package representative;
    private Long productionOutputId;
    private PageResult<Package> packages;

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Package extends RdPackageUnitDO {
        private Boolean eligible;
        private Long activeInspectionId;
        private String activeInspectionNo;
    }
}
