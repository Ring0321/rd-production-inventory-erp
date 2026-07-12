package com.ruiding.erp.service.packageunit;

import com.ruiding.erp.enums.RdPackageStatusEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RdPackageStatusMachineTest {

    @Test
    void shouldAllowFirstDeliveryLifecycle() {
        assertDoesNotThrow(() -> RdPackageStatusMachine.requireTransition(
                RdPackageStatusEnum.CREATED, RdPackageStatusEnum.LABELED));
        assertDoesNotThrow(() -> RdPackageStatusMachine.requireTransition(
                RdPackageStatusEnum.LABELED, RdPackageStatusEnum.QC_PENDING));
        assertDoesNotThrow(() -> RdPackageStatusMachine.requireTransition(
                RdPackageStatusEnum.QC_PENDING, RdPackageStatusEnum.QC_PASSED));
        assertDoesNotThrow(() -> RdPackageStatusMachine.requireTransition(
                RdPackageStatusEnum.QC_PASSED, RdPackageStatusEnum.INBOUND_PENDING));
        assertDoesNotThrow(() -> RdPackageStatusMachine.requireTransition(
                RdPackageStatusEnum.INBOUND_PENDING, RdPackageStatusEnum.IN_STOCK));
    }

    @Test
    void shouldRejectSkippingQualityInspection() {
        assertThrows(IllegalStateException.class, () -> RdPackageStatusMachine.requireTransition(
                RdPackageStatusEnum.LABELED, RdPackageStatusEnum.IN_STOCK));
    }
}
