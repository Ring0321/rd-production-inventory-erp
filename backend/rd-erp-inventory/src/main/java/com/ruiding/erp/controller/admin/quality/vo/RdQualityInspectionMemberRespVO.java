package com.ruiding.erp.controller.admin.quality.vo;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class RdQualityInspectionMemberRespVO {
    private Long packageId;
    private String packageCode;
    private BigDecimal packageWeight;
    // NULL for legacy rows: no historical sampling fact is reconstructed.
    private Boolean sampled;
}
