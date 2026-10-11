package com.ruiding.erp.controller.admin.quality.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RdQualityInspectionBatchCreateReqVO {
    @NotNull @Positive private Long packageId;
    @NotEmpty @Size(max = 200) private List<@NotNull @Positive Long> packageIds;
    @NotEmpty @Size(max = 200) private List<@NotNull @Positive Long> samplePackageIds;
    @Valid @NotEmpty private List<@NotNull RdQualityInspectionItemReqVO> items;
    @Size(max = 512) private String remark;
    @NotBlank @Size(max = 128) private String clientRequestId;
}
