package com.ruiding.erp.service.weight;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.ruiding.erp.service.weight.RdWeightSolverTest.*;
import static org.junit.jupiter.api.Assertions.*;

class RdWeightNativeRuntimeTest {
    @Test void rejectsKnownCrashingMsvcVersionsInsteadOfInvokingTheSolver() {
        assertFalse(RdWeightSolver.compatibleMsvcVersion(14, 29));
        assertFalse(RdWeightSolver.compatibleMsvcVersion(14, 36));
        assertTrue(RdWeightSolver.compatibleMsvcVersion(14, 40));
        assertTrue(RdWeightSolver.compatibleMsvcVersion(14, 42));
        assertTrue(RdWeightSolver.compatibleMsvcVersion(14, 50));
        assertFalse(RdWeightSolver.compatibleMsvcVersion(0, 0));
    }

    @Test void unsupportedActualRuntimeFailsClosedWithoutNativeSolve() {
        boolean supported = RdWeightSolver.nativeRuntimeSupported();
        if (Boolean.getBoolean("rd.weight.test.expectUnavailable")) assertFalse(supported);
        if (supported) return;
        var result = new RdWeightSolver().solve(List.of(bag(1, "500")), Map.of(10L, 1),
                1, kg("500"), kg("0"), 2);
        assertEquals("UNAVAILABLE", result.status());
        assertNull(result.totalWeightKg());
        assertTrue(result.bags().isEmpty());
    }
}
