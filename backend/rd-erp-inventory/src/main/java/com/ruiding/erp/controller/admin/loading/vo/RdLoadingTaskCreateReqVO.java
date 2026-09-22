package com.ruiding.erp.controller.admin.loading.vo;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class RdLoadingTaskCreateReqVO {
    @NotNull private Long pickupSlipId;
    @NotNull private Long forkliftDriverId;
    @Valid @NotEmpty private List<Selection> selections;
    private String remark;

    @Data
    public static class Selection {
        @NotNull private Long pickupSlipLineId;
        @NotNull private Long packageId;
    }
}
