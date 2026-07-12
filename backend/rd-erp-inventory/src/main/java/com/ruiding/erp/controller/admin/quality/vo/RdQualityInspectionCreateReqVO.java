package com.ruiding.erp.controller.admin.quality.vo;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionItemDO;
import javax.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;
@Data
public class RdQualityInspectionCreateReqVO {
    @NotNull private Long packageId;
    private List<RdQualityInspectionItemDO> items;
    private String remark;
}
