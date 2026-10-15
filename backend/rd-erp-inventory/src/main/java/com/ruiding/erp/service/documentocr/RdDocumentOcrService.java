package com.ruiding.erp.service.documentocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

@Service
public class RdDocumentOcrService {
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final int MAX_RESPONSE = 1024 * 1024;
    private static final List<String> FIELDS = List.of("customer", "order", "model", "bagCount", "weightKg");
    private static final Set<String> ERRORS = Set.of("OCR_BUSY", "OCR_TIMEOUT", "OCR_UNAVAILABLE", "OCR_INVALID_FILE",
            "OCR_FILE_TOO_LARGE", "OCR_PAGE_LIMIT", "OCR_PIXEL_LIMIT", "OCR_OUTPUT_LIMIT", "OCR_INFERENCE_FAILED");
    private final URI base;
    private final boolean enabled;
    private final Semaphore slot = new Semaphore(1);
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client;

    public RdDocumentOcrService(@Value("${RD_OCR_BASE_URL:http://127.0.0.1:48110}") String baseUrl,
                                @Value("${rd.document-ocr.enabled:false}") boolean enabled) {
        this.base = validateBaseUrl(baseUrl);
        this.enabled = enabled;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER).proxy(new ProxySelector() {
                    @Override public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
                    @Override public void connectFailed(URI uri, SocketAddress address, IOException error) { }
                }).build();
    }

    static URI validateBaseUrl(String url) {
        if (url == null || !url.matches("http://127\\.0\\.0\\.1:[0-9]{1,5}")) {
            throw new IllegalArgumentException("OCR origin must be a literal HTTP loopback origin");
        }
        URI uri = URI.create(url);
        if (uri.getPort() < 1024 || uri.getPort() > 65535) throw new IllegalArgumentException("Invalid OCR port");
        return uri;
    }

    public JsonNode status() {
        ObjectNode status = mapper.createObjectNode();
        status.put("available", false).put("state", enabled ? "UNAVAILABLE" : "DISABLED").put("device", "cpu");
        status.put("maxBytes", MAX_BYTES).put("maxPages", 3).put("timeoutSeconds", 60).put("concurrency", 1);
        if (!enabled) return status;
        try {
            JsonNode upstream = exchange("/status", null, 3);
            if (upstream.path("available").isBoolean() && upstream.path("state").isTextual()
                    && Set.of("LOCAL_FILES_READY", "MODELS_OR_RUNTIME_MISSING", "INFERENCE_FAILED").contains(upstream.path("state").textValue())) {
                status.put("available", upstream.path("available").booleanValue()).put("state", upstream.path("state").textValue());
            }
        } catch (RdDocumentOcrException ignored) { }
        return status;
    }

    public JsonNode recognize(MultipartFile file) {
        if (!enabled) throw new RdDocumentOcrException("OCR_UNAVAILABLE", 503);
        if (!slot.tryAcquire()) throw new RdDocumentOcrException("OCR_BUSY", 429);
        try {
            return validateResult(exchange("/recognize", readFile(file), 70));
        } finally {
            slot.release();
        }
    }

    static byte[] readFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new RdDocumentOcrException("OCR_INVALID_FILE", 415);
        if (file.getSize() > MAX_BYTES) throw new RdDocumentOcrException("OCR_FILE_TOO_LARGE", 413);
        try (var input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new RdDocumentOcrException("OCR_FILE_TOO_LARGE", 413);
            boolean png = bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8), new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
            boolean jpeg = bytes.length >= 3 && bytes[0] == (byte) 255 && bytes[1] == (byte) 216 && bytes[2] == (byte) 255;
            boolean pdf = bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-';
            if (!png && !jpeg && !pdf) throw new RdDocumentOcrException("OCR_INVALID_FILE", 415);
            return bytes;
        } catch (IOException error) {
            throw new RdDocumentOcrException("OCR_INVALID_FILE", 415);
        }
    }

    private JsonNode exchange(String path, byte[] body, int seconds) {
        HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(seconds))
                .header("Accept", "application/json");
        if (body == null) request.GET();
        else request.header("Content-Type", "application/octet-stream").POST(HttpRequest.BodyPublishers.ofByteArray(body));
        CompletableFuture<HttpResponse<byte[]>> future = client.sendAsync(request.build(), info -> new LimitedBody());
        try {
            HttpResponse<byte[]> response = future.get(seconds, TimeUnit.SECONDS);
            int httpStatus = response.statusCode();
            // Redirect responses are never followed, even to another loopback endpoint.
            if (httpStatus >= 300 && httpStatus < 400) throw new RdDocumentOcrException("OCR_UNAVAILABLE", 503);
            JsonNode result = mapper.readTree(response.body());
            if (httpStatus != 200) {
                String code = result == null ? "" : result.path("error").asText();
                if (!ERRORS.contains(code)) throw new RdDocumentOcrException("OCR_UNAVAILABLE", 503);
                int safeStatus = switch (code) {
                    case "OCR_BUSY" -> 429;
                    case "OCR_TIMEOUT" -> 504;
                    case "OCR_INVALID_FILE" -> 415;
                    case "OCR_FILE_TOO_LARGE" -> 413;
                    case "OCR_PAGE_LIMIT", "OCR_PIXEL_LIMIT", "OCR_OUTPUT_LIMIT" -> 422;
                    default -> 503;
                };
                throw new RdDocumentOcrException(code, safeStatus);
            }
            if (result == null || !result.isObject()) throw new RdDocumentOcrException("OCR_BAD_RESPONSE", 503);
            return result;
        } catch (TimeoutException error) {
            future.cancel(true);
            throw new RdDocumentOcrException("OCR_TIMEOUT", 504);
        } catch (InterruptedException error) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new RdDocumentOcrException("OCR_UNAVAILABLE", 503);
        } catch (ExecutionException | IOException error) {
            future.cancel(true);
            if (error.getCause() instanceof java.net.http.HttpTimeoutException) {
                throw new RdDocumentOcrException("OCR_TIMEOUT", 504);
            }
            throw new RdDocumentOcrException("OCR_UNAVAILABLE", 503);
        }
    }

    static JsonNode validateResult(JsonNode result) {
        require(result.isObject() && result.path("schemaVersion").asInt() == 1
                && "PaddleOCR".equals(result.path("engine").asText()) && "cpu".equals(result.path("device").asText())
                && "PP-OCRv5_mobile".equals(result.path("model").asText()));
        int pages = result.path("pages").asInt();
        require(pages >= 1 && pages <= 3);
        JsonNode lines = result.path("rawLines");
        require(lines.isArray() && lines.size() <= 600);
        Map<String, String> evidence = new HashMap<>();
        for (JsonNode line : lines) {
            String id = text(line, "id", 30);
            require(id.matches("p[1-3]-l[1-9][0-9]{0,2}") && !evidence.containsKey(id));
            require(line.path("page").isIntegralNumber() && line.path("page").asInt() >= 1 && line.path("page").asInt() <= pages);
            require(id.startsWith("p" + line.path("page").asInt() + "-"));
            evidence.put(id, text(line, "text", 500));
            checkConfidence(line);
            onlyKeys(line, Set.of("id", "page", "text", "confidence"));
        }
        JsonNode fields = result.path("candidates");
        require(fields.isObject());
        onlyKeys(fields, new HashSet<>(FIELDS));
        for (String key : FIELDS) {
            JsonNode field = fields.path(key);
            JsonNode candidates = field.path("candidates");
            require(candidates.isArray() && candidates.size() <= 30);
            require((candidates.isEmpty() ? "UNKNOWN" : "CANDIDATE").equals(field.path("state").asText()));
            onlyKeys(field, Set.of("state", "candidates"));
            for (JsonNode candidate : candidates) {
                text(candidate, "value", 160);
                JsonNode sources = candidate.path("sourceLineIds");
                require(sources.isArray() && sources.size() >= 1 && sources.size() <= 2);
                List<String> snippets = new ArrayList<>();
                for (JsonNode source : sources) {
                    require(source.isTextual() && evidence.containsKey(source.textValue()));
                    snippets.add(evidence.get(source.textValue()));
                }
                require(String.join("\n", snippets).equals(text(candidate, "evidence", 1001)));
                checkConfidence(candidate);
                onlyKeys(candidate, Set.of("value", "sourceLineIds", "evidence", "confidence"));
            }
        }
        onlyKeys(result, Set.of("schemaVersion", "engine", "model", "device", "pages", "rawLines", "candidates"));
        return result;
    }

    private static void checkConfidence(JsonNode node) {
        if (!node.has("confidence")) return;
        JsonNode score = node.get("confidence");
        require(score.isNumber() && Double.isFinite(score.doubleValue()) && score.doubleValue() >= 0 && score.doubleValue() <= 1);
    }

    private static String text(JsonNode node, String key, int max) {
        JsonNode value = node.path(key);
        require(value.isTextual() && !value.textValue().isBlank() && value.textValue().length() <= max);
        return value.textValue();
    }

    private static void onlyKeys(JsonNode node, Set<String> keys) {
        node.fieldNames().forEachRemaining(key -> require(keys.contains(key)));
    }

    private static void require(boolean condition) {
        if (!condition) throw new RdDocumentOcrException("OCR_BAD_RESPONSE", 503);
    }

    private static class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription subscription) { this.subscription = subscription; subscription.request(1); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (bytes.size() + buffer.remaining() > MAX_RESPONSE) {
                    subscription.cancel();
                    result.completeExceptionally(new IOException("OCR response limit"));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { result.completeExceptionally(error); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
