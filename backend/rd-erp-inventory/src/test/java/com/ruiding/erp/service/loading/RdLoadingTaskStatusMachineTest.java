package com.ruiding.erp.service.loading;

import com.ruiding.erp.enums.RdLoadingTaskActionEnum;
import com.ruiding.erp.enums.RdLoadingTaskActorEnum;
import com.ruiding.erp.enums.RdLoadingTaskStatusEnum;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RdLoadingTaskStatusMachineTest {
    private static final RdLoadingTaskActorEnum KEEPER = RdLoadingTaskActorEnum.WAREHOUSE_KEEPER;
    private static final RdLoadingTaskActorEnum DRIVER = RdLoadingTaskActorEnum.FORKLIFT_DRIVER;

    @Test
    void shouldFollowPickupSlipToConfirmedOutboundFlow() {
        RdLoadingTaskStatusEnum status = RdLoadingTaskStatusEnum.PENDING_ASSIGNMENT;
        status = RdLoadingTaskStatusMachine.transition(status, RdLoadingTaskActionEnum.ASSIGN, KEEPER);
        status = RdLoadingTaskStatusMachine.transition(status, RdLoadingTaskActionEnum.START, KEEPER);
        status = RdLoadingTaskStatusMachine.transition(status, RdLoadingTaskActionEnum.COMPLETE_SCANNING, KEEPER);
        status = RdLoadingTaskStatusMachine.transition(status, RdLoadingTaskActionEnum.CONFIRM_OUTBOUND, KEEPER);
        assertEquals(RdLoadingTaskStatusEnum.OUTBOUNDED, status);
    }

    @Test
    void shouldOnlyAllowAssignmentChangesBeforeLoading() {
        assertEquals(RdLoadingTaskStatusEnum.ASSIGNED, RdLoadingTaskStatusMachine.transition(
                RdLoadingTaskStatusEnum.ASSIGNED, RdLoadingTaskActionEnum.ADJUST_ASSIGNMENT, KEEPER));
        assertFalse(RdLoadingTaskStatusMachine.canTransition(
                RdLoadingTaskStatusEnum.LOADING, RdLoadingTaskActionEnum.ADJUST_ASSIGNMENT, KEEPER));
        assertFalse(RdLoadingTaskStatusMachine.canTransition(
                RdLoadingTaskStatusEnum.ASSIGNED, RdLoadingTaskActionEnum.ADJUST_ASSIGNMENT, DRIVER));
    }

    @Test
    void shouldFreezeLoadingOnExceptionUntilKeeperReassignsOrCancels() {
        RdLoadingTaskStatusEnum status = RdLoadingTaskStatusMachine.transition(
                RdLoadingTaskStatusEnum.LOADING, RdLoadingTaskActionEnum.REPORT_EXCEPTION, DRIVER);
        assertEquals(RdLoadingTaskStatusEnum.EXCEPTION_PENDING, status);
        assertTrue(RdLoadingTaskStatusMachine.allowedActions(status, DRIVER).isEmpty());
        assertEquals(RdLoadingTaskStatusEnum.ASSIGNED,
                RdLoadingTaskStatusMachine.transition(status, RdLoadingTaskActionEnum.REASSIGN, KEEPER));
        assertEquals(RdLoadingTaskStatusEnum.CANCELLED,
                RdLoadingTaskStatusMachine.transition(status, RdLoadingTaskActionEnum.CANCEL, KEEPER));
    }

    @Test
    void shouldAllowKeeperToReturnReviewWithoutPosting() {
        assertEquals(RdLoadingTaskStatusEnum.ASSIGNED, RdLoadingTaskStatusMachine.transition(
                RdLoadingTaskStatusEnum.PENDING_REVIEW, RdLoadingTaskActionEnum.RETURN_TO_DRIVER, KEEPER));
        assertFalse(RdLoadingTaskStatusMachine.canTransition(
                RdLoadingTaskStatusEnum.PENDING_REVIEW, RdLoadingTaskActionEnum.CANCEL, KEEPER));
    }

    @Test
    void driverCanNeverConfirmOrCancelOutbound() {
        Arrays.stream(RdLoadingTaskStatusEnum.values()).forEach(status ->
                Arrays.stream(RdLoadingTaskActionEnum.values())
                        .filter(action -> RdLoadingTaskStatusMachine.canTransition(status, action, DRIVER))
                        .forEach(action -> assertNotEquals(RdLoadingTaskStatusEnum.OUTBOUNDED,
                                RdLoadingTaskStatusMachine.transition(status, action, DRIVER))));
        assertThrows(IllegalStateException.class, () -> RdLoadingTaskStatusMachine.transition(
                RdLoadingTaskStatusEnum.PENDING_REVIEW, RdLoadingTaskActionEnum.CONFIRM_OUTBOUND, DRIVER));
        assertFalse(RdLoadingTaskStatusMachine.canTransition(
                RdLoadingTaskStatusEnum.ASSIGNED, RdLoadingTaskActionEnum.START, DRIVER));
    }

    @Test
    void shouldRejectIllegalAndTerminalTransitions() {
        assertThrows(IllegalStateException.class, () -> RdLoadingTaskStatusMachine.transition(
                RdLoadingTaskStatusEnum.ASSIGNED, RdLoadingTaskActionEnum.CONFIRM_OUTBOUND, KEEPER));
        assertThrows(IllegalStateException.class, () -> RdLoadingTaskStatusMachine.transition(
                RdLoadingTaskStatusEnum.LOADING, RdLoadingTaskActionEnum.CANCEL, KEEPER));
        assertTrue(RdLoadingTaskStatusMachine.allowedActions(RdLoadingTaskStatusEnum.OUTBOUNDED, KEEPER).isEmpty());
        assertTrue(RdLoadingTaskStatusMachine.allowedActions(RdLoadingTaskStatusEnum.CANCELLED, DRIVER).isEmpty());
        assertEquals(7, RdLoadingTaskStatusEnum.values().length);
    }
}
