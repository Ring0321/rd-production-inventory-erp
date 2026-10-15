package com.ruiding.erp.service.weight;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

class RdWeightSolverWorkerTest {
    static BigDecimal kg(String value) { return new BigDecimal(value); }

    static RdWeightSolver.Bag bag(long id, String weight) {
        return new RdWeightSolver.Bag(id, "PK-" + id, kg(weight), 20L, "A-01", 10L);
    }

    static RdWeightSolverWorker.Request request() {
        return new RdWeightSolverWorker.Request(List.of(bag(1, "500.001"), bag(2, "499.999")),
                Map.of(10L, 2), 2, kg("1000"), kg("0"), 2);
    }

    @Test void requestRoundtripPreservesExactFactsAndSnapshotsCollections() throws Exception {
        var bags = new ArrayList<>(request().bags());
        var caps = new HashMap<>(request().lotCaps());
        var snapshot = new RdWeightSolverWorker.Request(bags, caps, 2, kg("1000"), kg("0"), 2);
        bags.clear();
        caps.clear();
        assertEquals(request(), RdWeightSolverWorker.readRequest(
                new ByteArrayInputStream(RdWeightSolverWorker.encodeRequest(snapshot))));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.bags().clear());
    }

    @Test void inputRejectsOversizedOrAmbiguousJsonAndRuntimeFields() throws Exception {
        String valid = new String(RdWeightSolverWorker.encodeRequest(request()), StandardCharsets.UTF_8);
        for (String invalid : List.of("", "null", valid + " {}", valid.replace("\"seconds\":2.0", "\"seconds\":2.0,\"seconds\":1"),
                valid.replace("\"maxCount\":2", "\"maxCount\":2.9"), valid.replace("\"maxCount\":2", "\"maxCount\":\"2\""),
                valid.replace("\"seconds\":2.0", "\"seconds\":null"), valid.replace("\"seconds\":2.0", "\"seconds\":3"),
                valid.substring(0, valid.length() - 1) + ",\"java\":\"untrusted\"}",
                "[".repeat(50) + "0" + "]".repeat(50))) {
            assertThrows(Exception.class, () -> RdWeightSolverWorker.readRequest(bytes(invalid)), invalid);
        }
        byte[] exactLimit = padded(valid.getBytes(StandardCharsets.UTF_8), RdWeightSolverWorker.MAX_INPUT_BYTES);
        assertEquals(request(), RdWeightSolverWorker.readRequest(new ByteArrayInputStream(exactLimit)));
        assertThrows(Exception.class, () -> RdWeightSolverWorker.readRequest(new ByteArrayInputStream(
                padded(valid.getBytes(StandardCharsets.UTF_8), RdWeightSolverWorker.MAX_INPUT_BYTES + 1))));
    }

    @Test void requestRejectsInvalidBudgetsIdentitiesCapacitiesAndWeights() {
        assertThrows(IllegalArgumentException.class, () -> new RdWeightSolverWorker.Request(
                LongStream.rangeClosed(1, 501).mapToObj(id -> bag(id, "1")).toList(), Map.of(10L, 501), 500, kg("1"), kg("0"), 2));
        for (double seconds : new double[]{-1, 2.01, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> new RdWeightSolverWorker.Request(
                    request().bags(), request().lotCaps(), 2, kg("1000"), kg("0"), seconds));
        for (List<RdWeightSolver.Bag> bags : List.of(List.of(bag(1, "0")), List.of(bag(1, "1.0001")),
                List.of(bag(1, "1"), bag(1, "1")), List.of(new RdWeightSolver.Bag(1L, "X".repeat(257), kg("1"), 20L, "A", 10L))))
            assertThrows(IllegalArgumentException.class, () -> new RdWeightSolverWorker.Request(
                    bags, Map.of(10L, 2), 2, kg("1000"), kg("0"), 2));
        assertThrows(IllegalArgumentException.class, () -> new RdWeightSolverWorker.Request(
                request().bags(), Map.of(10L, -1), 2, kg("1000"), kg("0"), 2));
        assertThrows(IllegalArgumentException.class, () -> new RdWeightSolverWorker.Request(
                request().bags(), Map.of(), 2, kg("1000"), kg("0"), 2));
    }

    @Test void acceptsVerifiedSuccessAndKnownNonSuccessStatusesOnly() throws Exception {
        var facts = request();
        var result = new RdWeightSolver.Result("OPTIMAL", facts.bags(), kg("1000.000"), 2.0);
        var accepted = RdWeightSolverWorker.readResult(RdWeightSolverWorker.encodeResult(result), facts);
        assertEquals(result, accepted);
        assertSame(facts.bags().get(0), RdWeightSolverWorker.validateResult(result, facts).bags().get(0));
        for (String status : List.of("INFEASIBLE", "UNKNOWN", "MODEL_INVALID", "UNAVAILABLE")) {
            var empty = new RdWeightSolver.Result(status, List.of(), null, null);
            assertEquals(empty, RdWeightSolverWorker.readResult(RdWeightSolverWorker.encodeResult(empty), request()));
        }
    }

    @Test void rejectsForgedIdsFactsTotalsCountsCapacitiesStatusesAndBounds() {
        var facts = request();
        var changed = new RdWeightSolver.Bag(1L, "forged", kg("500.001"), 20L, "A-01", 10L);
        for (var result : List.of(
                new RdWeightSolver.Result("OPTIMAL", List.of(bag(99, "1000")), kg("1000"), 1.0),
                new RdWeightSolver.Result("OPTIMAL", List.of(changed, bag(2, "499.999")), kg("1000"), 2.0),
                new RdWeightSolver.Result("OPTIMAL", List.of(bag(1, "500.001"), bag(1, "500.001")), kg("1000.002"), 2.0),
                new RdWeightSolver.Result("OPTIMAL", facts.bags(), kg("999"), 2.0),
                new RdWeightSolver.Result("FEASIBLE", List.of(bag(1, "500.001")), kg("500.001"), 1.0),
                new RdWeightSolver.Result("OPTIMAL", facts.bags(), kg("1000"), 0.0),
                new RdWeightSolver.Result("FEASIBLE", facts.bags(), kg("1000"), 3.0),
                new RdWeightSolver.Result("OPTIMAL", facts.bags(), kg("1000"), Double.NaN),
                new RdWeightSolver.Result("SUCCESS", facts.bags(), kg("1000"), 2.0),
                new RdWeightSolver.Result("UNKNOWN", facts.bags(), null, null),
                new RdWeightSolver.Result("INFEASIBLE", List.of(), kg("0"), null)))
            assertThrows(IllegalArgumentException.class, () -> RdWeightSolverWorker.validateResult(result, facts));
        assertThrows(IllegalArgumentException.class, () -> RdWeightSolverWorker.validateResult(
                new RdWeightSolver.Result("OPTIMAL", facts.bags(), kg("1000"), 2.0),
                new RdWeightSolverWorker.Request(facts.bags(), Map.of(10L, 1), 2, kg("1000"), kg("0"), 2)));
        assertThrows(IllegalArgumentException.class, () -> RdWeightSolverWorker.validateResult(
                new RdWeightSolver.Result("OPTIMAL", facts.bags(), kg("1000"), 2.0),
                new RdWeightSolverWorker.Request(facts.bags(), facts.lotCaps(), 1, kg("1000"), kg("0"), 2)));
    }

    @Test void outputIsBoundedAndDoesNotAcceptTrailingOrPartialJson() throws Exception {
        byte[] valid = RdWeightSolverWorker.encodeResult(new RdWeightSolver.Result("UNKNOWN", List.of(), null, null));
        assertEquals("UNKNOWN", RdWeightSolverWorker.readResult(padded(valid, RdWeightSolverWorker.MAX_OUTPUT_BYTES), request()).status());
        for (byte[] invalid : List.of(padded(valid, RdWeightSolverWorker.MAX_OUTPUT_BYTES + 1),
                (new String(valid, StandardCharsets.UTF_8) + "{}").getBytes(StandardCharsets.UTF_8), "{}".getBytes(StandardCharsets.UTF_8)))
            assertThrows(Exception.class, () -> RdWeightSolverWorker.readResult(invalid, request()));
    }

    @Test void resultCanExceedIndividualWeightLimitWithinAuthorizedTolerance() {
        var bags = List.of(bag(1, "600000"), bag(2, "600000"));
        var facts = new RdWeightSolverWorker.Request(bags, Map.of(10L, 2), 2, kg("1000000"), kg("300000"), 2);
        var result = new RdWeightSolver.Result("FEASIBLE", bags, kg("1200000"), 0.0);
        assertEquals(0, kg("1200000").compareTo(RdWeightSolverWorker.validateResult(result, facts).totalWeightKg()));
    }

    @Test void boundariesAccept500BagsAndRejectLargeEncodedPayloads() throws Exception {
        var bags = LongStream.rangeClosed(1, 500).mapToObj(id -> bag(id, "1")).toList();
        var facts = new RdWeightSolverWorker.Request(bags, Map.of(10L, 500), 500, kg("500"), kg("0"), 2);
        assertEquals(facts, RdWeightSolverWorker.readRequest(new ByteArrayInputStream(RdWeightSolverWorker.encodeRequest(facts))));
        var result = new RdWeightSolver.Result("OPTIMAL", bags, kg("500.000"), 500.0);
        assertEquals(result, RdWeightSolverWorker.readResult(RdWeightSolverWorker.encodeResult(result), facts));
        String wide = "\u4e2d".repeat(256);
        var wideBags = LongStream.rangeClosed(1, 500).mapToObj(id -> new RdWeightSolver.Bag(
                id, id + wide.substring(Long.toString(id).length()), kg("1"), 20L, wide, 10L)).toList();
        var tooWide = new RdWeightSolverWorker.Request(wideBags, Map.of(10L, 500), 500, kg("500"), kg("0"), 2);
        assertThrows(Exception.class, () -> RdWeightSolverWorker.encodeRequest(tooWide));
        assertThrows(Exception.class, () -> RdWeightSolverWorker.encodeResult(new RdWeightSolver.Result("OPTIMAL", wideBags, kg("500"), 500.0)));
    }

    @Test void invalidWorkerInputReturnsOnlyUnavailableJson() throws Exception {
        var out = new ByteArrayOutputStream();
        RdWeightSolverWorker.run(bytes("{\"java\":\"no\"}"), out);
        assertEquals("UNAVAILABLE", RdWeightSolverWorker.readResult(out.toByteArray(), request()).status());
    }

    private static ByteArrayInputStream bytes(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }

    static byte[] padded(byte[] json, int length) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) ' ');
        System.arraycopy(json, 0, bytes, 0, json.length);
        return bytes;
    }
}
