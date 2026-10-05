package com.ruiding.erp.controller.admin.packageunit.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Package digital identity and trace response")
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RdPackageTraceRespVO {

    private String packageCode;
    private String digitalLink;
    private String status;
    private String statusName;
    private LocalDate productionDate;
    private LocalDate furnaceOutDate;
    private String shiftCode;
    private String productName;
    private String productModel;
    private String furnaceNo;
    private String furnaceLayer;
    private BigDecimal packageWeight;
    private String qualityStatus;
    private String qualityConclusion;
    private Boolean internalView;

    @Schema(description = "Internal field; omitted from public response")
    private String customerName;

    @Schema(description = "Internal field; omitted from public response")
    private String locationCode;

    @Schema(description = "Internal field; omitted from public response")
    private Long productionOutputId;

    private List<RdPackageTraceEventRespVO> events;
}
