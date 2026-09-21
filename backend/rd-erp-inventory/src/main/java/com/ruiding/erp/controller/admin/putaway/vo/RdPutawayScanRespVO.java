package com.ruiding.erp.controller.admin.putaway.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class RdPutawayScanRespVO {
    private Long recordId;
    private Long taskId;
    private String packageCode;
    private String scanResult;
    private Boolean success;
    private Boolean replayed;
    private String message;
    private String taskStatus;
    private Integer scannedPackageCount;
    private Integer packageCount;
    private LocalDateTime scanTime;
}
