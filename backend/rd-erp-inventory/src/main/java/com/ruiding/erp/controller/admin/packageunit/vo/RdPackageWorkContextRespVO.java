package com.ruiding.erp.controller.admin.packageunit.vo;

import lombok.Data;
import lombok.experimental.Accessors;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RdPackageWorkContextRespVO {
    private String packageCode;
    private List<Task> tasks;
    private Long pickupSlipId;
    private String pickupSlipNo;

    @Data
    @Accessors(chain = true)
    public static class Task {
        private String type;
        private Long taskId;
        private String taskNo;
        private String status;
        private Boolean myTask;
    }
}
