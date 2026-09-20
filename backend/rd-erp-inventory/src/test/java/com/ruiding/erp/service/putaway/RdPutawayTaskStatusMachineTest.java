package com.ruiding.erp.service.putaway;

import com.ruiding.erp.enums.RdPutawayTaskActionEnum;
import com.ruiding.erp.enums.RdPutawayTaskActorEnum;
import com.ruiding.erp.enums.RdPutawayTaskStatusEnum;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RdPutawayTaskStatusMachineTest {

    @Test
    void shouldFollowNormalWarehouseAndDriverFlow() {
        RdPutawayTaskStatusEnum status = RdPutawayTaskStatusEnum.PENDING_PUTAWAY;
        status = RdPutawayTaskStatusMachine.transition(status, RdPutawayTaskActionEnum.ASSIGN);
        status = RdPutawayTaskStatusMachine.transition(status, RdPutawayTaskActionEnum.START);
        status = RdPutawayTaskStatusMachine.transition(status, RdPutawayTaskActionEnum.COMPLETE_SCANNING);
        status = RdPutawayTaskStatusMachine.transition(status, RdPutawayTaskActionEnum.CONFIRM_PUTAWAY);

        assertEquals(RdPutawayTaskStatusEnum.PUTAWAY_COMPLETED, status);
    }

    @Test
    void shouldRequireWarehouseToResolveDriverException() {
        RdPutawayTaskStatusEnum status = RdPutawayTaskStatusMachine.transition(
                RdPutawayTaskStatusEnum.MOVING, RdPutawayTaskActionEnum.REPORT_EXCEPTION);

        assertEquals(RdPutawayTaskStatusEnum.EXCEPTION_PENDING, status);
        assertFalse(RdPutawayTaskStatusMachine.canTransition(status, RdPutawayTaskActionEnum.START));
        assertEquals(RdPutawayTaskStatusEnum.ASSIGNED,
                RdPutawayTaskStatusMachine.transition(status, RdPutawayTaskActionEnum.REASSIGN));
    }

    @Test
    void forkliftDriverActionMustNeverCompletePutaway() {
        Arrays.stream(RdPutawayTaskStatusEnum.values()).forEach(status ->
                Arrays.stream(RdPutawayTaskActionEnum.values())
                        .filter(action -> action.getActor() == RdPutawayTaskActorEnum.FORKLIFT_DRIVER)
                        .filter(action -> RdPutawayTaskStatusMachine.canTransition(status, action))
                        .forEach(action -> assertNotEquals(
                                RdPutawayTaskStatusEnum.PUTAWAY_COMPLETED,
                                RdPutawayTaskStatusMachine.transition(status, action)
                        )));

        assertEquals(RdPutawayTaskActorEnum.WAREHOUSE_KEEPER,
                RdPutawayTaskActionEnum.CONFIRM_PUTAWAY.getActor());
    }

    @Test
    void shouldRejectIllegalOrTerminalTransitions() {
        assertThrows(IllegalStateException.class, () -> RdPutawayTaskStatusMachine.transition(
                RdPutawayTaskStatusEnum.ASSIGNED, RdPutawayTaskActionEnum.CONFIRM_PUTAWAY));
        assertTrue(RdPutawayTaskStatusMachine.allowedActions(
                RdPutawayTaskStatusEnum.PUTAWAY_COMPLETED).isEmpty());
        assertTrue(RdPutawayTaskStatusMachine.allowedActions(
                RdPutawayTaskStatusEnum.CANCELLED).isEmpty());
    }
}
