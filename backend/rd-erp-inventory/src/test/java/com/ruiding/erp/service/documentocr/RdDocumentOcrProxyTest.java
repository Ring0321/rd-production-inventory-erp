package com.ruiding.erp.service.documentocr;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RdDocumentOcrProxyTest {
    @Test void redirectsAreNotFollowedAndRemoteErrorBodiesAreNotExposed() throws Exception {
        AtomicInteger redirectedCalls = new AtomicInteger();
        HttpServer target = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        target.createContext("/", exchange -> { redirectedCalls.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close(); });
        target.start();
        HttpServer redirect = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        redirect.createContext("/recognize", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + target.getAddress().getPort() + "/private");
            exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        redirect.start();
        try {
            var service = new RdDocumentOcrService("http://127.0.0.1:" + redirect.getAddress().getPort(), true);
            var error = assertThrows(RdDocumentOcrException.class, () -> service.recognize(pdf()));
            assertEquals("OCR_UNAVAILABLE", error.getMessage());
            assertEquals(0, redirectedCalls.get());
        } finally { redirect.stop(0); target.stop(0); }
    }

    @Test void errorsAndOversizedOrInvalidServiceResponsesHaveBoundedSafeContracts() throws Exception {
        for (String payload : new String[]{"{\"error\":\"secret file path /private/test\"}", "not json", "x".repeat(1024 * 1024 + 1)}) {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/recognize", exchange -> {
                exchange.getRequestBody().readAllBytes();
                byte[] data = payload.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(503, data.length);
                try (var output = exchange.getResponseBody()) { output.write(data); } catch (Exception ignored) { }
            });
            server.start();
            try {
                var service = new RdDocumentOcrService("http://127.0.0.1:" + server.getAddress().getPort(), true);
                var error = assertThrows(RdDocumentOcrException.class, () -> service.recognize(pdf()));
                assertEquals("OCR_UNAVAILABLE", error.getMessage());
            } finally { server.stop(0); }
        }
    }

    @Test void disabledStatusMakesNoConnectionAndUnavailableStatusNeverPretendsReady() throws Exception {
        var disabled = new RdDocumentOcrService("http://127.0.0.1:48110", false);
        assertEquals("DISABLED", disabled.status().path("state").asText());
        assertFalse(disabled.status().path("available").asBoolean());
        var error = assertThrows(RdDocumentOcrException.class, () -> disabled.recognize(pdf()));
        assertEquals("OCR_UNAVAILABLE", error.getMessage());
        HttpServer listener = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = listener.getAddress().getPort();
        listener.stop(0);
        assertFalse(new RdDocumentOcrService("http://127.0.0.1:" + port, true).status().path("available").asBoolean());
    }

    private MockMultipartFile pdf() { return new MockMultipartFile("file", "synthetic.pdf", "application/pdf", "%PDF-1.7\n".getBytes(StandardCharsets.UTF_8)); }
}
