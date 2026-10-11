package com.ruiding.erp.service.ai;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

@Component
public class RdGlmClient {
    static final URI ENDPOINT = URI.create("https://open.bigmodel.cn/api/paas/v4/chat/completions");
    static final int MAX_RESPONSE_BYTES = 32768;
    private final RdAiProperties config;
    private final ObjectMapper json = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final HttpClient http;

    @Autowired
    public RdGlmClient(RdAiProperties config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1).build());
    }
    RdGlmClient(RdAiProperties config, HttpClient http) { this.config = config; this.http = http; }

    // This is the complete export schema. Never replace it with a trace/DO serialization.
    public record Input(String status, String qualityStage, Integer packageCount, BigDecimal recordedWeight,
                        Map<String, Integer> counts, String question, List<String> allowedPoints) { }
    public record Result(List<String> points, Integer totalTokens) { }
    public static class Failure extends RuntimeException {
        private final String state;
        public Failure(String state) { super("GLM request did not produce a verified answer"); this.state = state; }
        public String state() { return state; }
    }

    String payload(Input input) {
        try {
            String context = json.writeValueAsString(input);
            if (context.length() > 12000) throw new IllegalArgumentException("Context limit exceeded");
            return json.writeValueAsString(Map.of("model", config.getModel(), "stream", false,
                    "max_tokens", config.getMaxOutputTokens(), "thinking", Map.of("type", "disabled"),
                    "response_format", Map.of("type", "json_object"), "request_id", UUID.randomUUID().toString(),
                    "messages", List.of(Map.of("role", "system", "content",
                            "Order the most relevant allowedPoints for the question using the given ERP states and recorded source aggregates. "
                            + "Return ONLY a JSON object {\"points\":[\"KEY\"]}, 1 to 5 unique keys copied from allowedPoints. "
                            + "No text, numbers, decisions, tools, or new keys. IN_STOCK is not available stock. "
                            + "Recorded weights are not scale measurements. Pending quality is not failure."),
                            Map.of("role", "user", "content", context))));
        } catch (Exception e) { throw new Failure("INVALID_OUTPUT"); }
    }

    public Result ask(Input input) {
        if (!"READY".equals(config.state())) throw new Failure(config.state());
        HttpRequest request = HttpRequest.newBuilder(ENDPOINT).timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + config.getApiKey()).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload(input), StandardCharsets.UTF_8)).build();
        CompletableFuture<HttpResponse<byte[]>> pending = http.sendAsync(request, ignored -> new LimitedBody());
        try {
            HttpResponse<byte[]> response = pending.get(20, TimeUnit.SECONDS);
            int status = response.statusCode();
            if (status != 200) throw new Failure(Set.of(400, 401, 403, 404, 429).contains(status) ? "FAILED" : "UNKNOWN");
            if (!response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT).contains("application/json")) {
                throw new Failure("INVALID_OUTPUT");
            }
            return parse(new String(response.body(), StandardCharsets.UTF_8), input.allowedPoints());
        } catch (Failure e) { throw e;
        } catch (IllegalArgumentException e) { throw new Failure("INVALID_OUTPUT");
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new Failure("UNKNOWN");
        } catch (Exception e) { throw new Failure("UNKNOWN");
        } finally { if (!pending.isDone()) pending.cancel(true); }
    }

    Result parse(String body, List<String> allowed) {
        try {
            if (body.getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) throw new IllegalArgumentException();
            JsonNode root = json.readTree(body);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.size() != 1) throw new IllegalArgumentException();
            JsonNode choice = choices.get(0), message = choice.path("message");
            if (!"stop".equals(choice.path("finish_reason").asText()) || !"assistant".equals(message.path("role").asText())
                    || message.has("tool_calls") || message.has("refusal") || !message.path("content").isTextual()) {
                throw new IllegalArgumentException();
            }
            String content = message.path("content").asText();
            if (content.length() > 2048) throw new IllegalArgumentException();
            JsonNode selected = json.readTree(content);
            JsonNode points = selected.path("points");
            if (!selected.isObject() || selected.size() != 1 || !points.isArray() || points.size() < 1 || points.size() > 5) {
                throw new IllegalArgumentException();
            }
            List<String> keys = new ArrayList<>();
            for (JsonNode point : points) {
                if (!point.isTextual() || !allowed.contains(point.asText()) || keys.contains(point.asText())) throw new IllegalArgumentException();
                keys.add(point.asText());
            }
            Integer usage = null;
            JsonNode tokens = root.path("usage").path("total_tokens");
            if (!tokens.isMissingNode() && !tokens.isNull()) {
                if (!tokens.isIntegralNumber() || !tokens.canConvertToInt() || tokens.intValue() < 0) throw new IllegalArgumentException();
                usage = tokens.intValue();
            }
            return new Result(List.copyOf(keys), usage);
        } catch (Exception e) { throw new IllegalArgumentException("Unverified model output"); }
    }

    // Bound memory before buffering and bound total time even while receiving a slow body.
    static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > MAX_RESPONSE_BYTES - bytes.size()) {
                    subscription.cancel(); result.completeExceptionally(new IllegalArgumentException("Response limit exceeded")); return;
                }
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { result.completeExceptionally(new IllegalArgumentException("Response unavailable")); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
