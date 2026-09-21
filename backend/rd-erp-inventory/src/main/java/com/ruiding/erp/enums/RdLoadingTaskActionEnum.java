package com.ruiding.erp.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RdLoadingTaskActionEnum {
    ASSIGN(RdLoadingTaskActorEnum.WAREHOUSE_KEEPER),
    ADJUST_ASSIGNMENT(RdLoadingTaskActorEnum.WAREHOUSE_KEEPER),
    START(RdLoadingTaskActorEnum.FORKLIFT_DRIVER),
    COMPLETE_SCANNING(RdLoadingTaskActorEnum.FORKLIFT_DRIVER),
    REPORT_EXCEPTION(RdLoadingTaskActorEnum.FORKLIFT_DRIVER),
    REASSIGN(RdLoadingTaskActorEnum.WAREHOUSE_KEEPER),
    RETURN_TO_DRIVER(RdLoadingTaskActorEnum.WAREHOUSE_KEEPER),
    CONFIRM_OUTBOUND(RdLoadingTaskActorEnum.WAREHOUSE_KEEPER),
    CANCEL(RdLoadingTaskActorEnum.WAREHOUSE_KEEPER);

    private final RdLoadingTaskActorEnum actor;
}
