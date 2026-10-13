package com.ruiding.erp.service.packageunit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruiding.erp.controller.admin.packageunit.vo.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RdPackagePassportServiceTest {
    @BeforeEach void tenant() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }
    @Mock RdPackageUnitService packages;
    @Spy RdDigitalLinkService links = new RdDigitalLinkService("http://localhost:9000/pkg");
    @InjectMocks RdPackagePassportService service;
    private RdPackageTraceRespVO prepare() {
        var trace = new RdPackageTraceRespVO().setPackageCode("P1").setDigitalLink("https://id.tjldsic.com/pkg/P1")
            .setInternalView(false).setStatus("QC_PENDING").setQualityStatus("SUBMITTED").setQualityConclusion("PASS")
            .setPackageWeight(new BigDecimal("840.000000")).setProductModel("PUBLIC_MODEL")
            .setCustomerName("PRIVATE_CUSTOMER").setLocationCode("PRIVATE_LOCATION").setProductionOutputId(9876L)
            .setEvents(List.of(new RdPackageTraceEventRespVO().setStage("QUALITY").setTitle("PRIVATE_OPERATOR")
                .setDetail("PRIVATE_ORDER").setEventTime(LocalDateTime.of(2026, 10, 13, 9, 0))));
        when(packages.getTraceDetail("P1", false)).thenReturn(trace);
        return trace;
    }
    @Test void explicitProjectionKeepsPrivateValuesOutAndPendingIsNotPassed() throws Exception {
        prepare();
        var r = service.read("P1");
        String body = r.passport().canonicalJson();
        for (String value : List.of("PRIVATE_CUSTOMER", "PRIVATE_LOCATION", "PRIVATE_OPERATOR", "PRIVATE_ORDER", "9876", "productionOutputId")) assertFalse(body.contains(value));
        var json = new ObjectMapper().readTree(body);
        assertTrue(json.path("workflow").path("confirmedQuality").isNull());
        assertEquals("840.000000", json.path("product").path("weightKg").asText());
        assertEquals("https://id.tjldsic.com/pkg/P1", json.path("@id").asText());
        assertTrue(new ObjectMapper().writeValueAsString(r.linkset()).contains("http://localhost:9000/pkg/P1?resource=passport"));
        verify(packages, never()).generate(anyLong(), anyInt());
    }
    @Test void digestIsExactBytesStableAcrossQueriesAndPrivateEditsButChangesWithPublicFacts() throws Exception {
        var trace = prepare();
        var first = service.read("P1").passport();
        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(first.canonicalJson().getBytes(StandardCharsets.UTF_8)));
        assertEquals(expected, first.digest());
        trace.setCustomerName("ANOTHER_PRIVATE_VALUE").setLocationCode("OTHER_LOCATION");
        assertEquals(first.digest(), service.read("P1").passport().digest());
        trace.setPackageWeight(new BigDecimal("697"));
        assertNotEquals(first.digest(), service.read("P1").passport().digest());
    }
    @Test void sourceIdentityAndPublicFlagAreMandatory() {
        var trace = prepare();
        trace.setInternalView(true);
        assertThrows(IllegalStateException.class, () -> service.read("P1"));
        trace.setInternalView(false).setPackageCode("P2");
        assertThrows(IllegalStateException.class, () -> service.read("P1"));
        trace.setPackageCode("P1").setDigitalLink("https://id.tjldsic.com/pkg/P2");
        assertThrows(IllegalArgumentException.class, () -> service.read("P1"));
    }
    @Test void unknownValuesRemainNullAndPrivateEventsCannotEnterPublicPassport() throws Exception {
        var trace = prepare();
        trace.setPackageWeight(null).setQualityStatus("LEGACY_UNKNOWN").setStatus("LEGACY_UNKNOWN")
            .setEvents(List.of(new RdPackageTraceEventRespVO().setStage("EXCEPTION").setTitle("PRIVATE_OPERATOR").setEventTime(LocalDateTime.now())));
        var json = new ObjectMapper().readTree(service.read("P1").passport().canonicalJson());
        assertTrue(json.path("product").path("weightKg").isNull());
        assertEquals("UNKNOWN", json.path("workflow").path("status").asText());
        assertEquals(0, json.path("events").size());
    }
    @Test void onlyMatchingReviewedQualityIsConfirmedAndInputDtoIsUnchanged() throws Exception {
        var trace = prepare().setQualityStatus("PASSED").setQualityConclusion("PASSED");
        var before = new ObjectMapper().findAndRegisterModules().writeValueAsString(trace);
        var r = service.read("P1");
        assertEquals("PASSED", ((Map<?, ?>) r.document().get("workflow")).get("confirmedQuality"));
        assertEquals(before, new ObjectMapper().findAndRegisterModules().writeValueAsString(trace));
        trace.setQualityConclusion("REJECTED");
        assertNull(((Map<?, ?>) service.read("P1").document().get("workflow")).get("confirmedQuality"));
    }
}
