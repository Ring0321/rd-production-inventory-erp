package com.ruiding.erp.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class RdGlmClientTest {
    private final RdAiProperties config = new RdAiProperties();
    private final RdGlmClient client = new RdGlmClient(config);

    @Test void disabledAndIncompleteConfigurationNeverOpensTransport() {
        assertEquals("DISABLED", config.state());
        assertThrows(RdGlmClient.Failure.class, () -> client.ask(null));
        config.setEnabled(true);
        assertEquals("UNCONFIGURED", config.state());
        config.setApiKey("local-protocol-fixture");
        config.setModel("glm-fixture");
        assertEquals("COST_NOT_APPROVED", config.state());
        config.setCostApproved(true);
        assertEquals("LIMIT_NOT_SET", config.state());
        config.setDailyCallLimit(10);
        assertEquals("READY", config.state());
        config.setMaxOutputTokens(9000);
        assertEquals("LIMIT_NOT_SET", config.state());
    }

    @Test void boundedSubscriberRejectsOversizeBeforeBufferingAndStopsDemand() {
        var body = new RdGlmClient.LimitedBody();
        var subscription = org.mockito.Mockito.mock(Flow.Subscription.class);
        body.onSubscribe(subscription);
        body.onNext(List.of(ByteBuffer.allocate(RdGlmClient.MAX_RESPONSE_BYTES + 1)));
        org.mockito.Mockito.verify(subscription).cancel();
        assertTrue(body.getBody().toCompletableFuture().isCompletedExceptionally());
    }

    @Test void validUsageIsReturnedButNegativeAndFabricatedUsageIsRejected() {
        var base = response("{\"points\":[\"QUALITY_PENDING\"]}", "stop", "");
        var used = base.substring(0, base.length() - 1) + ",\"usage\":{\"total_tokens\":123}}";
        assertEquals(123, client.parse(used, List.of("QUALITY_PENDING")).totalTokens());
        for (var invalid : List.of("-1", "2.5", "\"free\"", "99999999999999999")) {
            var broken = base.substring(0, base.length() - 1) + ",\"usage\":{\"total_tokens\":" + invalid + "}}";
            assertThrows(IllegalArgumentException.class, () -> client.parse(broken, List.of("QUALITY_PENDING")));
        }
    }

    @Test void localMockedHttpProtocolUsesOneOfficialRequestAndValidatesResponse() {
        ready();
        var transport = mock(HttpClient.class);
        var adapter = new RdGlmClient(config, transport);
        var res = mock(HttpResponse.class);
        when(res.statusCode()).thenReturn(200);
        when(res.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type", List.of("application/json")), (a, b) -> true));
        when(res.body()).thenReturn(response("{\"points\":[\"QUALITY_PENDING\"]}", "stop", "").getBytes(StandardCharsets.UTF_8));
        when(transport.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(CompletableFuture.completedFuture(res));
        var input = new RdGlmClient.Input("QC_PENDING", "SUBMITTED", 2, new java.math.BigDecimal("2000"),
                Map.of("QC_PENDING", 2), "STATUS", List.of("QUALITY_PENDING"));
        assertEquals(List.of("QUALITY_PENDING"), adapter.ask(input).points());
        verify(transport).sendAsync(argThat((HttpRequest r) -> ENDPOINT_EQUALS(r) && r.method().equals("POST")
                && r.timeout().orElseThrow().toSeconds() == 20), any(HttpResponse.BodyHandler.class));
    }

    @Test void timeoutReturnsUnknownAndCancelsWithoutAnyAutomaticRetry() {
        ready();
        var transport = mock(HttpClient.class);
        var pending = new CompletableFuture<HttpResponse<byte[]>>() {
            @Override public HttpResponse<byte[]> get(long timeout, TimeUnit unit) throws TimeoutException { throw new TimeoutException(); }
        };
        when(transport.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(pending);
        var adapter = new RdGlmClient(config, transport);
        var input = new RdGlmClient.Input("IN_STOCK", "UNKNOWN", null, null, Map.of(), "STATUS", List.of("STOCK_NOT_AVAILABLE"));
        var error = assertThrows(RdGlmClient.Failure.class, () -> adapter.ask(input));
        assertEquals("UNKNOWN", error.state()); assertTrue(pending.isCancelled());
        verify(transport, times(1)).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    private boolean ENDPOINT_EQUALS(HttpRequest r) { return r.uri().equals(RdGlmClient.ENDPOINT); }
    private void ready() {
        config.setEnabled(true); config.setApiKey("local-protocol-fixture"); config.setModel("glm-fixture");
        config.setCostApproved(true); config.setDailyCallLimit(10);
    }

    @Test void payloadContainsOnlyControlledContextAndUsesOfficialEndpoint() throws Exception {
        config.setModel("glm-fixture");
        var input = new RdGlmClient.Input("QC_PENDING", "SUBMITTED", 3, new java.math.BigDecimal("2800"),
                Map.of("IN_STOCK", 2, "QC_PENDING", 1), "STATUS", List.of("QUALITY_PENDING", "STOCK_NOT_AVAILABLE"));
        var json = new ObjectMapper().readTree(client.payload(input));
        assertEquals("glm-fixture", json.path("model").asText());
        assertEquals("json_object", json.path("response_format").path("type").asText());
        assertFalse(json.path("stream").asBoolean());
        assertFalse(json.has("tools"));
        assertFalse(json.has("user_id"));
        assertEquals("https://open.bigmodel.cn/api/paas/v4/chat/completions", RdGlmClient.ENDPOINT.toString());
        var context = new ObjectMapper().readTree(json.path("messages").get(1).path("content").asText());
        assertEquals(7, context.size());
        assertEquals(3, context.path("packageCount").asInt());
        for (String forbidden : List.of("packageCode", "customerName", "locationCode", "furnaceNo",
                "events", "remark", "userId", "tenantId", "productionOutputId")) assertFalse(context.has(forbidden));
    }

    @Test void modelCanOnlySelectUniqueCurrentKeysAndActualUsageIsNullable() {
        var answer = client.parse(response("{\"points\":[\"QUALITY_PENDING\"]}", "stop", ""), List.of("QUALITY_PENDING"));
        assertEquals(List.of("QUALITY_PENDING"), answer.points());
        assertNull(answer.totalTokens());
        for (String content : List.of("{\"points\":[\"SHIP_NOW\"]}",
                "{\"points\":[\"QUALITY_PENDING\"]}{\"action\":\"SHIP_NOW\"}",
                "{\"points\":[\"QUALITY_PENDING\"]} ship now",
                "{\"points\":[\"QUALITY_PENDING\",\"QUALITY_PENDING\"]}",
                "{\"points\":[\"QUALITY_PENDING\"],\"text\":\"invented\"}",
                "```json\n{\"points\":[\"QUALITY_PENDING\"]}\n```")) {
            assertThrows(IllegalArgumentException.class, () -> client.parse(response(content, "stop", ""), List.of("QUALITY_PENDING")));
        }
        assertThrows(IllegalArgumentException.class, () -> client.parse(response("{\"points\":[\"QUALITY_PENDING\"]}", "length", ""), List.of("QUALITY_PENDING")));
        assertThrows(IllegalArgumentException.class, () -> client.parse(response("{\"points\":[\"QUALITY_PENDING\"]}", "stop", ",\"tool_calls\":[{}]"), List.of("QUALITY_PENDING")));
        assertThrows(IllegalArgumentException.class, () -> client.parse(response("{\"points\":[\"QUALITY_PENDING\"]}", "stop", "") + "{}", List.of("QUALITY_PENDING")));
    }

    private String response(String content, String finish, String messageExtras) {
        try {
            return "{\"choices\":[{\"finish_reason\":\"" + finish + "\",\"message\":{\"role\":\"assistant\",\"content\":"
                    + new ObjectMapper().writeValueAsString(content) + messageExtras + "}}]}";
        } catch (Exception e) { throw new AssertionError(e); }
    }
}
