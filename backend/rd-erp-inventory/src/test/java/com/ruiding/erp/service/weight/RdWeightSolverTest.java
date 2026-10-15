package com.ruiding.erp.service.weight;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RdWeightSolverTest {
    private final RdWeightSolver solver = new RdWeightSolver();

    @Test void selectsActualTailBagsWithoutRounding() {
        var bags = List.of(bag(1, "500.001"), bag(2, "499.999"), bag(3, "501"));
        var result = solver.solve(bags, Map.of(10L, 3), 3, kg("1000"), kg("0"), 2);
        assertEquals("OPTIMAL", result.status());
        assertEquals(List.of(1L, 2L), result.bags().stream().map(RdWeightSolver.Bag::packageId).toList());
        assertEquals(0, result.totalWeightKg().compareTo(kg("1000")));
    }

    @Test void honorsLotReservationAndLineCountCaps() {
        var result = solver.solve(List.of(bag(1, "500"), bag(2, "500")),
                Map.of(10L, 1), 2, kg("1000"), kg("0"), 2);
        assertEquals("INFEASIBLE", result.status());
        assertTrue(result.bags().isEmpty());
        assertNull(result.totalWeightKg());
        result = solver.solve(List.of(bag(1, "500"), bag(2, "500")),
                Map.of(10L, 2), 1, kg("1000"), kg("0"), 2);
        assertEquals("INFEASIBLE", result.status());
    }

    @Test void reportsSubsetInfeasibilityInsteadOfReturningAnOutOfTolerancePlan() {
        var result = solver.solve(List.of(bag(1, "600"), bag(2, "600")),
                Map.of(10L, 2), 2, kg("1000"), kg("20"), 2);
        assertEquals("INFEASIBLE", result.status());
        assertTrue(result.bags().isEmpty());
    }

    @Test void timeoutWithoutIncumbentIsUnknownNotInfeasibleOrOptimal() {
        var result = solver.solve(List.of(bag(1, "500")), Map.of(10L, 1),
                1, kg("500"), kg("0"), 0);
        assertEquals("UNKNOWN", result.status());
        assertTrue(result.bags().isEmpty());
    }

    @Test void rejectsUnsupportedPrecisionAndDuplicateIdentity() {
        assertThrows(IllegalArgumentException.class, () -> RdWeightSolver.grams(kg("1.0001")));
        assertEquals(1001, RdWeightSolver.grams(kg("1.0010")));
        assertThrows(IllegalArgumentException.class, () -> solver.solve(
                List.of(bag(1, "500"), bag(1, "500")), Map.of(10L, 2), 2,
                kg("1000"), kg("0"), 2));
    }

    @Test void emptyEligibleInventoryIsInfeasibleNotAZeroWeightPlan() {
        var result = solver.solve(List.of(), Map.of(), 1, kg("1000"), kg("0"), 2);
        assertEquals("INFEASIBLE", result.status());
        assertTrue(result.bags().isEmpty());
        assertNull(result.totalWeightKg());
    }

    @Test void optimizesDeviationBeforeBagCountWithinTolerance() {
        var result = solver.solve(List.of(bag(1, "999"), bag(2, "500"), bag(3, "500")),
                Map.of(10L, 3), 3, kg("1000"), kg("2"), 2);
        assertEquals("OPTIMAL", result.status());
        assertEquals(List.of(2L, 3L), result.bags().stream().map(RdWeightSolver.Bag::packageId).toList());
    }

    static RdWeightSolver.Bag bag(long id, String weight) {
        return new RdWeightSolver.Bag(id, "PK-" + id, kg(weight), 20L, "A-01", 10L);
    }
    static BigDecimal kg(String value) { return new BigDecimal(value); }
}
