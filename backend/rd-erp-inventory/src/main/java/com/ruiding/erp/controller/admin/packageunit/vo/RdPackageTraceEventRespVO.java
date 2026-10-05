package com.ruiding.erp.controller.admin.packageunit.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Schema(description = "Package trace event")
@Data
@Accessors(chain = true)
public class RdPackageTraceEventRespVO {

    @Schema(description = "Stage code")
    private String stage;

    @Schema(description = "Display title")
    private String title;

    @Schema(description = "Event time")
    private LocalDateTime eventTime;

    @Schema(description = "Sanitized event detail")
    private String detail;
}
