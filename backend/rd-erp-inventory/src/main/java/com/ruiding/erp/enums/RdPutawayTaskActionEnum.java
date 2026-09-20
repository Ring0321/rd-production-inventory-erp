package com.ruiding.erp.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RdPutawayTaskActionEnum {
    ASSIGN(RdPutawayTaskActorEnum.WAREHOUSE_KEEPER),
    START(RdPutawayTaskActorEnum.FORKLIFT_DRIVER),
    COMPLETE_SCANNING(RdPutawayTaskActorEnum.FORKLIFT_DRIVER),
    REPORT_EXCEPTION(RdPutawayTaskActorEnum.FORKLIFT_DRIVER),
    REASSIGN(RdPutawayTaskActorEnum.WAREHOUSE_KEEPER),
    RETURN_TO_DRIVER(RdPutawayTaskActorEnum.WAREHOUSE_KEEPER),
    CONFIRM_PUTAWAY(RdPutawayTaskActorEnum.WAREHOUSE_KEEPER),
    CANCEL(RdPutawayTaskActorEnum.WAREHOUSE_KEEPER);

    private final RdPutawayTaskActorEnum actor;
}
