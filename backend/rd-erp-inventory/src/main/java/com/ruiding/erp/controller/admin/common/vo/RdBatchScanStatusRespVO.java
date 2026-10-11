package com.ruiding.erp.controller.admin.common.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
public class RdBatchScanStatusRespVO {
    public enum State { APPLIED, NOT_APPLIED_STALE, UNKNOWN }

    private Long taskId;
    private Integer requestVersion;
    private Integer currentVersion;
    private String taskStatus;
    private State state;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long recordId;
    private String message;

    public static RdBatchScanStatusRespVO of(Long taskId, Integer requestVersion, Integer currentVersion,
                                            String taskStatus, Long verifiedRecordId) {
        State state = verifiedRecordId != null ? State.APPLIED
                : requestVersion != null && currentVersion != null && currentVersion > requestVersion
                    ? State.NOT_APPLIED_STALE : State.UNKNOWN;
        String message = switch (state) {
            case APPLIED -> "The original batch scan request was applied.";
            case NOT_APPLIED_STALE -> "No record exists and the task version has advanced; this request cannot be confirmed.";
            case UNKNOWN -> "No record exists and the request version is not older; the request may still arrive.";
        };
        return new RdBatchScanStatusRespVO().setTaskId(taskId).setRequestVersion(requestVersion)
                .setCurrentVersion(currentVersion).setTaskStatus(taskStatus).setState(state)
                .setRecordId(verifiedRecordId).setMessage(message);
    }
}
