package com.ruiding.erp.controller.admin.putaway.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class RdPutawayBatchScanConfirmReqVO extends RdPutawayScanReqVO {
    @NotNull
    private Integer version;
    @NotEmpty @Size(max = 500)
    private List<@NotNull @Positive Long> taskLineIds;
}
