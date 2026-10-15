package com.ruiding.erp.service.documentocr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruiding.erp.controller.admin.documentocr.RdDocumentOcrController;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RdDocumentOcrControllerTest {
    @Test void routeAcceptsExactlyOneFileAndNeverAcceptsUrlOrPathParameters() throws Exception {
        var service = mock(RdDocumentOcrService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new RdDocumentOcrController(service)).build();
        var file = new MockMultipartFile("file", "synthetic.pdf", "application/pdf", "%PDF-1.7\n".getBytes());
        for (String parameter : new String[]{"url", "path", "command", "baseUrl"}) {
            mvc.perform(multipart("/rd/document-ocr/recognize").file(file).param(parameter, "untrusted"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.msg").value("OCR_INVALID_REQUEST"));
        }
        mvc.perform(multipart("/rd/document-ocr/recognize").file(file).file(file)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void successAndStatusAreNoStoreAndRetainOriginalCommonResultEnvelope() throws Exception {
        var service = mock(RdDocumentOcrService.class);
        var mapper = new ObjectMapper();
        when(service.status()).thenReturn(mapper.readTree("{\"available\":false,\"state\":\"DISABLED\"}"));
        when(service.recognize(any())).thenReturn(mapper.readTree("{\"schemaVersion\":1}"));
        var mvc = MockMvcBuilders.standaloneSetup(new RdDocumentOcrController(service)).build();
        mvc.perform(get("/rd/document-ocr/status")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.state").value("DISABLED"));
        mvc.perform(multipart("/rd/document-ocr/recognize").file(new MockMultipartFile("file", new byte[]{1})))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.schemaVersion").value(1));
    }
}
