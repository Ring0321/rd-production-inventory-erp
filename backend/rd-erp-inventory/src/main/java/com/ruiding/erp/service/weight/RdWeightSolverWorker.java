package com.ruiding.erp.service.weight;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class RdWeightSolverWorker {
    public static final String ARGUMENT = "--weight-solver-worker";
    static final int MAX_INPUT_BYTES = 256 * 1024;
    static final int MAX_OUTPUT_BYTES = 256 * 1024;
    private static final Set<String> EMPTY_STATUSES = Set.of("INFEASIBLE", "UNKNOWN", "MODEL_INVALID", "UNAVAILABLE");
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);

    private RdWeightSolverWorker() { }

    public record Request(List<RdWeightSolver.Bag> bags, Map<Long, Integer> lotCaps, int maxCount,
                          BigDecimal targetKg, BigDecimal toleranceKg, double seconds) {
        public Request {
            RdWeightSolver.validateInput(bags, lotCaps, maxCount, targetKg, toleranceKg, seconds);
            bags = List.copyOf(bags);
            lotCaps = Map.copyOf(lotCaps);
        }
    }

    public static void main(String[] args) { run(System.in, System.out); }

    public static void run(InputStream input, OutputStream output) {
        RdWeightSolver.Result result;
        try {
            var request = readRequest(input);
            // Deliberately bypass solve(): a worker never launches another worker.
            result = validateResult(new RdWeightSolver().solveLocally(request), request);
        } catch (IOException | RuntimeException | LinkageError ex) {
            result = unavailable();
        }
        try {
            output.write(encodeResult(result));
            output.flush();
        } catch (IOException | RuntimeException ex) {
            // A closed or over-budget pipe must not produce diagnostics on the JSON channel.
        }
    }

    static RdWeightSolver.Result unavailable() { return new RdWeightSolver.Result("UNAVAILABLE", List.of(), null, null); }

    static Request readRequest(InputStream input) throws IOException {
        Request request = decode(readBounded(input, MAX_INPUT_BYTES), Request.class);
        if (request == null) throw new IOException("Missing worker request");
        return request;
    }

    static byte[] encodeRequest(Request request) throws IOException { return encode(request, MAX_INPUT_BYTES); }

    static byte[] encodeResult(RdWeightSolver.Result result) throws IOException { return encode(result, MAX_OUTPUT_BYTES); }

    static RdWeightSolver.Result readResult(byte[] bytes, Request facts) throws IOException {
        if (bytes.length > MAX_OUTPUT_BYTES) throw new IOException("Worker output exceeds budget");
        return validateResult(decode(bytes, RdWeightSolver.Result.class), facts);
    }

    static RdWeightSolver.Result validateResult(RdWeightSolver.Result result, Request facts) {
        if (result == null || result.status() == null || result.bags() == null)
            throw new IllegalArgumentException("Missing worker result");
        if (EMPTY_STATUSES.contains(result.status())) {
            if (!result.bags().isEmpty() || result.totalWeightKg() != null || result.objectiveBound() != null)
                throw new IllegalArgumentException("Non-solution contains a selection");
            return new RdWeightSolver.Result(result.status(), List.of(), null, null);
        }
        if (!("OPTIMAL".equals(result.status()) || "FEASIBLE".equals(result.status())) ||
                result.bags().isEmpty() || result.bags().size() > facts.maxCount())
            throw new IllegalArgumentException("Invalid solution status or count");
        Map<Long, RdWeightSolver.Bag> original = new HashMap<>();
        for (var bag : facts.bags()) original.put(bag.packageId(), bag);
        var ids = new HashSet<Long>();
        var lotCounts = new HashMap<Long, Integer>();
        var selected = new ArrayList<RdWeightSolver.Bag>();
        long total = 0;
        for (var returned : result.bags()) {
            var bag = returned == null ? null : original.get(returned.packageId());
            if (bag == null || !ids.add(bag.packageId()) || !sameFacts(bag, returned))
                throw new IllegalArgumentException("Worker selection differs from original facts");
            if (lotCounts.merge(bag.stockLotId(), 1, Integer::sum) > facts.lotCaps().get(bag.stockLotId()))
                throw new IllegalArgumentException("Worker exceeded lot capacity");
            total = Math.addExact(total, RdWeightSolver.grams(bag.packageWeightKg()));
            selected.add(bag);
        }
        long deviation = Math.abs(total - RdWeightSolver.grams(facts.targetKg()));
        BigDecimal verifiedTotal = BigDecimal.valueOf(total, 3);
        if (result.totalWeightKg() == null || verifiedTotal.compareTo(result.totalWeightKg()) != 0 ||
                deviation > RdWeightSolver.grams(facts.toleranceKg()))
            throw new IllegalArgumentException("Worker total is incorrect or outside tolerance");
        double objective = deviation * (facts.bags().size() + 1L) + selected.size();
        Double bound = result.objectiveBound();
        if (bound == null || !Double.isFinite(bound) || bound < 0 || bound > objective + 0.000001 ||
                "OPTIMAL".equals(result.status()) && Math.abs(bound - objective) > 0.000001)
            throw new IllegalArgumentException("Worker objective bound contradicts status");
        return new RdWeightSolver.Result(result.status(), List.copyOf(selected), verifiedTotal, bound);
    }

    private static boolean sameFacts(RdWeightSolver.Bag a, RdWeightSolver.Bag b) {
        return Objects.equals(a.packageCode(), b.packageCode()) && b.packageWeightKg() != null &&
                a.packageWeightKg().compareTo(b.packageWeightKg()) == 0 && Objects.equals(a.locationId(), b.locationId()) &&
                Objects.equals(a.locationCode(), b.locationCode()) && Objects.equals(a.stockLotId(), b.stockLotId());
    }

    static byte[] readBounded(InputStream input, int limit) throws IOException {
        var output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer, 0, Math.min(buffer.length, limit + 1 - output.size()))) != -1) {
            if (output.size() + count > limit) throw new IOException("Worker stream exceeds budget");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static <T> T decode(byte[] bytes, Class<T> type) throws IOException {
        // Jackson 2.13 has no StreamReadConstraints; bound nesting/tokens before databinding.
        try (JsonParser parser = JSON.getFactory().createParser(bytes)) {
            int depth = 0;
            int tokens = 0;
            JsonToken token;
            while ((token = parser.nextToken()) != null) {
                if (++tokens > 16000) throw new IOException("Worker JSON token budget exceeded");
                if (token == JsonToken.START_ARRAY || token == JsonToken.START_OBJECT) {
                    if (++depth > 8) throw new IOException("Worker JSON nesting budget exceeded");
                } else if (token == JsonToken.END_ARRAY || token == JsonToken.END_OBJECT) depth--;
                if ((token == JsonToken.VALUE_STRING || token == JsonToken.FIELD_NAME) && parser.getTextLength() > 256 ||
                        token.isNumeric() && parser.getTextLength() > 64)
                    throw new IOException("Worker JSON scalar budget exceeded");
            }
        }
        return JSON.readValue(bytes, type);
    }

    private static byte[] encode(Object value, int limit) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var bounded = new OutputStream() {
            @Override public void write(int b) throws IOException {
                if (bytes.size() == limit) throw new IOException("Worker JSON exceeds budget");
                bytes.write(b);
            }
            @Override public void write(byte[] b, int offset, int length) throws IOException {
                if (length > limit - bytes.size()) throw new IOException("Worker JSON exceeds budget");
                bytes.write(b, offset, length);
            }
        }) {
            JSON.writeValue(bounded, value);
        }
        return bytes.toByteArray();
    }
}
