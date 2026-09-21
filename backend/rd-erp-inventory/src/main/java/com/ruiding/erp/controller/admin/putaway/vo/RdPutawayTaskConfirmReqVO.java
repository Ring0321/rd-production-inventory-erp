package com.ruiding.erp.controller.admin.putaway.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class RdPutawayTaskConfirmReqVO {
    @NotNull(message = "上架任务不能为空")
    private Long id;

    @NotNull(message = "最终库位不能为空")
    private Long finalLocationId;

    @NotNull(message = "任务版本不能为空")
    private Integer version;
}
