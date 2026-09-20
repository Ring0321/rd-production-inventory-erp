package com.ruiding.erp.controller.admin.putaway.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
public class RdPutawayTaskReassignReqVO {
    @NotNull(message = "上架任务不能为空")
    private Long id;

    @NotNull(message = "计划库位不能为空")
    private Long plannedLocationId;

    @NotNull(message = "叉车司机不能为空")
    private Long forkliftDriverId;

    @NotNull(message = "任务版本不能为空")
    private Integer version;

    @NotBlank(message = "调整原因不能为空")
    @Size(max = 1000, message = "调整原因不能超过 1000 个字符")
    private String handlingResult;
}
