package com.ruiding.erp.service.ai;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.ruiding.erp.controller.admin.ai.vo.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RdPackageAiServiceTest {
    @Mock RdPackageAiFactsService facts;
    @Mock RdAiCallGuard guard;
    @Mock RdGlmClient client;
    @Spy RdAiProperties config = new RdAiProperties();
    @InjectMocks RdPackageAiService service;
    final RdPackageAiAskReqVO req = new RdPackageAiAskReqVO();
    RdPackageAiFactsService.Snapshot snapshot;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        req.setPackageCode("P1"); req.setQuestion(RdPackageAiAskReqVO.Question.STATUS); req.setRequestId(UUID.randomUUID().toString());
        config.setEnabled(true); config.setApiKey("protocol-fixture"); config.setModel("glm-fixture"); config.setCostApproved(true); config.setDailyCallLimit(5);
        var context = new RdPackageAiContext("P1", "IN_STOCK", "UNKNOWN", new RdPackageAiContext.Source("UNKNOWN", null, null, Map.of()),
                "2026-10-12T00:00:00Z", "READY", List.of(new RdPackageAiContext.Notice("READ_ONLY_AI", "local explanation")));
        snapshot = new RdPackageAiFactsService.Snapshot(context, "fingerprint");
        when(facts.read("P1", 1L)).thenReturn(snapshot);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void disabledModeReturnsActualFactsWithoutGuardOrTransport() {
        config.setEnabled(false);
        var a = service.ask(req, 1L);
        assertEquals("DISABLED", a.state()); assertSame(snapshot.context(), a.context()); assertTrue(a.noteKeys().isEmpty());
        verifyNoInteractions(guard, client);
    }

    @Test void requestReusePendingUnknownOrStaleCannotOpenTransport() {
        for (String state : List.of("PENDING", "UNKNOWN", "STALE", "LIMIT_REACHED", "FAILED")) {
            when(guard.reserve(anyLong(), anyLong(), anyString(), anyString(), any())).thenReturn(new RdAiCallGuard.Reservation("local-key", state, null));
            assertEquals(state, service.ask(req, 1L).state());
        }
        verifyNoInteractions(client);
        verify(guard, never()).finish(any(), any(), any());
    }

    @Test void guardFailureFailsClosedAndNoSensitiveExceptionIsReturned() {
        when(guard.reserve(anyLong(), anyLong(), anyString(), anyString(), any())).thenThrow(new RuntimeException("sensitive fixture"));
        var a = service.ask(req, 1L);
        assertEquals("GUARD_UNAVAILABLE", a.state()); assertFalse(a.message().contains("sensitive"));
        verifyNoInteractions(client);
    }

    @Test void successStoresOnlyVerifiedPointsUsageAndTimeAndRechecksFacts() {
        reserveNew();
        when(client.ask(any())).thenReturn(new RdGlmClient.Result(List.of("READ_ONLY_AI"), 120));
        var a = service.ask(req, 1L);
        assertEquals("SUCCESS", a.state()); assertEquals(120, a.totalTokens()); assertEquals(List.of("READ_ONLY_AI"), a.noteKeys());
        verify(facts, times(2)).read("P1", 1L);
        verify(guard).finish(any(), eq("SUCCESS"), argThat(s -> !s.contains("P1") && !s.contains("local explanation")));
        verify(guard).reserve(eq(1L), eq(1L), eq(req.getRequestId()), eq("fingerprint:STATUS"), same(config));
    }

    @Test void changedFactsAfterResponseCannotDisplayAStaleAnswer() {
        reserveNew();
        when(client.ask(any())).thenReturn(new RdGlmClient.Result(List.of("READ_ONLY_AI"), 100));
        when(facts.read("P1", 1L)).thenReturn(snapshot, new RdPackageAiFactsService.Snapshot(snapshot.context(), "changed"));
        var a = service.ask(req, 1L);
        assertEquals("STALE", a.state()); assertTrue(a.noteKeys().isEmpty());
    }

    @Test void invalidKeysAndUnknownNetworkResultsStaySeparateFromSuccess() {
        reserveNew();
        when(client.ask(any())).thenReturn(new RdGlmClient.Result(List.of("SHIP_NOW"), 100));
        assertEquals("INVALID_OUTPUT", service.ask(req, 1L).state());
        when(client.ask(any())).thenThrow(new RdGlmClient.Failure("UNKNOWN"));
        assertEquals("UNKNOWN", service.ask(req, 1L).state());
    }

    @Test void missingReceiptDoesNotTurnTransportSuccessIntoConfirmedSuccess() {
        reserveNew();
        when(client.ask(any())).thenReturn(new RdGlmClient.Result(List.of("READ_ONLY_AI"), null));
        doThrow(new RuntimeException()).when(guard).finish(any(), any(), any());
        assertEquals("UNKNOWN", service.ask(req, 1L).state());
    }

    @Test void cachedResultStillRequiresCurrentPackagePermission() {
        when(facts.read("P1", 1L)).thenThrow(new RuntimeException("permission withdrawn"));
        assertThrows(RuntimeException.class, () -> service.ask(req, 1L));
        verifyNoInteractions(guard, client);
    }

    @Test void readOnlyReconciliationWorksWhileDisabledAndNeverOpensTransport() {
        config.setEnabled(false);
        when(guard.lookup(eq(1L), eq(1L), eq(req.getRequestId()), eq("fingerprint:STATUS")))
                .thenReturn(new RdAiCallGuard.Reservation("local-key", "SUCCESS",
                        "{\"points\":[\"READ_ONLY_AI\"],\"totalTokens\":120,\"answeredAt\":\"2026-10-12T00:00:00Z\"}"));
        assertEquals("SUCCESS", service.result(req, 1L).state());
        verifyNoInteractions(client);
        verify(guard, never()).reserve(any(), any(), any(), any(), any());
    }

    @Test void expiredReconciliationIsNotARequestToGenerateAgain() {
        when(guard.lookup(any(), any(), any(), any())).thenReturn(new RdAiCallGuard.Reservation("local-key", "RECEIPT_MISSING", null));
        assertEquals("RECEIPT_MISSING", service.result(req, 1L).state());
        verifyNoInteractions(client);
        verify(guard, never()).reserve(any(), any(), any(), any(), any());
    }

    private void reserveNew() {
        when(guard.reserve(anyLong(), anyLong(), anyString(), anyString(), any())).thenReturn(new RdAiCallGuard.Reservation("local-key", "NEW", null));
        when(facts.export(snapshot, "STATUS")).thenReturn(new RdGlmClient.Input("IN_STOCK", "UNKNOWN", null, null, Map.of(), "STATUS", List.of("READ_ONLY_AI")));
    }
}
