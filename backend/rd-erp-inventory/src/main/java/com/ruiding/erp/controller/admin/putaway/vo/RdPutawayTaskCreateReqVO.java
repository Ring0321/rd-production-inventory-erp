package com.ruiding.erp.controller.admin.putaway.vo;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

@Data
public class RdPutawayTaskCreateReqVO {
    @NotNull(message = "计划库位不能为空")
    private Long plannedLocationId;

    @NotNull(message = "叉车司机不能为空")
    private Long forkliftDriverId;

    @NotEmpty(message = "请至少选择一个待上架包")
    @Size(max = 200, message = "单个上架任务最多选择 200 包")
    private List<Long> packageIds;

    @Size(max = 512, message = "备注不能超过 512 个字符")
    private String remark;
}
