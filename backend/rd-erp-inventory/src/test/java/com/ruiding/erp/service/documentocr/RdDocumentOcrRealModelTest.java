package com.ruiding.erp.service.documentocr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "rd.ocr.real", matches = "true")
class RdDocumentOcrRealModelTest {
    @Test void actualJavaProxyValidatesRealPaddleSyntheticDocument() throws Exception {
        var file = new MockMultipartFile("file", "synthetic-pickup.png", "image/png",
                Files.readAllBytes(Path.of(System.getProperty("rd.ocr.synthetic"))));
        var service = new RdDocumentOcrService("http://127.0.0.1:48110", true);
        assertTrue(service.status().path("available").asBoolean());
        var result = service.recognize(file);
        assertEquals("cpu", result.path("device").asText());
        assertEquals("PaddleOCR", result.path("engine").asText());
        assertEquals("000042", result.path("candidates").path("order").path("candidates").get(0).path("value").textValue());
        assertEquals("1250", result.path("candidates").path("weightKg").path("candidates").get(0).path("value").textValue());
    }
}
