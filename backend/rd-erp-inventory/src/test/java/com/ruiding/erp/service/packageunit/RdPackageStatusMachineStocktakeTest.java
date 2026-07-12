package com.ruiding.erp.service.packageunit;

import com.ruiding.erp.enums.RdPackageStatusEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class RdPackageStatusMachineStocktakeTest {

    @Test
    void shouldAllowStocktakeMissingRoundTrip() {
        assertDoesNotThrow(() -> RdPackageStatusMachine.requireTransition(
                RdPackageStatusEnum.IN_STOCK, RdPackageStatusEnum.STOCKTAKE_MISSING));
        assertDoesNotThrow(() -> RdPackageStatusMachine.requireTransition(
                RdPackageStatusEnum.STOCKTAKE_MISSING, RdPackageStatusEnum.IN_STOCK));
    }
}
