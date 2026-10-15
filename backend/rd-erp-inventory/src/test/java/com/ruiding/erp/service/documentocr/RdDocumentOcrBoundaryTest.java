package com.ruiding.erp.service.documentocr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruiding.erp.controller.admin.documentocr.RdDocumentOcrController;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RdDocumentOcrBoundaryTest {
    @Test void onlyLiteralFixedLoopbackOriginIsAllowed() {
        assertEquals(URI.create("http://127.0.0.1:48110"), RdDocumentOcrService.validateBaseUrl("http://127.0.0.1:48110"));
        for (String url : List.of("http://evil.test:48110", "http://localhost:48110", "https://127.0.0.1:48110",
                "http://127.0.0.1:48110@evil.test", "http://127.0.0.1:48110/path", "http://127.0.0.1:48110?url=x",
                "http://2130706433:48110", "http://127.1:48110", "file:///tmp/x", "http://[::1]:48110")) {
            assertThrows(IllegalArgumentException.class, () -> RdDocumentOcrService.validateBaseUrl(url), url);
        }
    }

    @Test void byteMagicAndBoundedReadRejectSpoofedFiles() {
        assertThrows(RdDocumentOcrException.class, () -> RdDocumentOcrService.readFile(
                new MockMultipartFile("file", "a.png", "image/png", "<svg/>".getBytes())));
        assertThrows(RdDocumentOcrException.class, () -> RdDocumentOcrService.readFile(
                new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[10 * 1024 * 1024 + 1])));
        assertDoesNotThrow(() -> RdDocumentOcrService.readFile(
                new MockMultipartFile("file", "ignored.any", "text/plain", "%PDF-1.7\n".getBytes())));
    }

    @Test void bothRoutesAreGuardedAndNeverLogDocumentPayloads() {
        for (var method : RdDocumentOcrController.class.getDeclaredMethods()) {
            if (!List.of("recognize", "status").contains(method.getName())) continue;
            assertEquals("@rdDocumentOcrAccess.allowed()", method.getAnnotation(PreAuthorize.class).value());
            assertFalse(method.getAnnotation(ApiAccessLog.class).requestEnable());
            assertFalse(method.getAnnotation(ApiAccessLog.class).responseEnable());
        }
    }

    @Test void responseSchemaRejectsNumericIdentifiersAndFakeEvidence() throws Exception {
        var mapper = new ObjectMapper();
        var body = mapper.readTree("{\"schemaVersion\":1,\"engine\":\"PaddleOCR\",\"model\":\"PP-OCRv5_mobile\",\"device\":\"cpu\",\"pages\":1,"
                + "\"rawLines\":[{\"id\":\"p1-l1\",\"page\":1,\"text\":\"订单号：00001\"}],\"candidates\":{}} ");
        for (String field : List.of("customer", "order", "model", "bagCount", "weightKg")) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) body.get("candidates")).set(field,
                    mapper.readTree("{\"state\":\"UNKNOWN\",\"candidates\":[]}"));
        }
        assertDoesNotThrow(() -> RdDocumentOcrService.validateResult(body));
        ((com.fasterxml.jackson.databind.node.ObjectNode) body.get("candidates")).set("order", mapper.readTree(
                "{\"state\":\"CANDIDATE\",\"candidates\":[{\"value\":1,\"sourceLineIds\":[\"p1-l1\"],\"evidence\":\"订单号：00001\"}]}"));
        assertThrows(RdDocumentOcrException.class, () -> RdDocumentOcrService.validateResult(body));
        ((com.fasterxml.jackson.databind.node.ObjectNode) body.get("candidates")).set("order", mapper.readTree(
                "{\"state\":\"CANDIDATE\",\"candidates\":[{\"value\":\"00001\",\"sourceLineIds\":[\"missing\"],\"evidence\":\"invented\"}]}"));
        assertThrows(RdDocumentOcrException.class, () -> RdDocumentOcrService.validateResult(body));
    }
}
