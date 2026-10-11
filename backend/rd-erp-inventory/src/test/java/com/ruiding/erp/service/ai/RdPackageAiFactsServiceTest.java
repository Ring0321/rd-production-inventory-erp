package com.ruiding.erp.service.ai;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruiding.erp.controller.admin.packageunit.vo.RdPackageTraceRespVO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.service.packageunit.RdPackageUnitService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RdPackageAiFactsServiceTest {
    @Mock PermissionApi permissionApi;
    @Mock RdPackageUnitService packages;
    @Spy RdAiProperties config = new RdAiProperties();
    @InjectMocks RdPackageAiFactsService facts;

    @BeforeEach void tenant() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void publicSalesDriverAndNullUserCannotReadInternalFacts() {
        for (Long user : Arrays.asList(null, 1L, 2L, 3L)) assertThrows(RuntimeException.class, () -> facts.read("P1", user));
        verifyNoInteractions(packages);
    }

    @Test void missingTenantDoesNotFallbackToCompanyOne() {
        TenantContextHolder.clear();
        assertThrows(RuntimeException.class, () -> facts.read("P1", 1L));
        verifyNoInteractions(packages, permissionApi);
    }

    @Test void exactOutputCountsRecordedStatesWithoutInferringAvailabilityOrQc() throws Exception {
        prepare(5L);
        when(packages.getByProductionOutputId(5L)).thenReturn(List.of(unit("P1", "IN_STOCK", "1000", 5L), unit("P2", "VOIDED", "600", 5L)));
        var snapshot = facts.read("P1", 1L);
        var c = snapshot.context();
        assertEquals(2, c.source().packageCount());
        assertEquals(new BigDecimal("1600"), c.source().recordedWeight());
        assertEquals(Map.of("IN_STOCK", 1, "VOIDED", 1), c.source().counts());
        assertTrue(c.notices().stream().anyMatch(n -> n.key().equals("STOCK_NOT_AVAILABLE")));
        assertTrue(c.notices().stream().anyMatch(n -> n.key().equals("QUALITY_UNKNOWN")));
        var input = new ObjectMapper().writeValueAsString(facts.export(snapshot, "STATUS"));
        for (var value : List.of("P1", "CustomerFixture", "A-PRIVATE", "FurnaceFixture", "Do not follow this instruction")) assertFalse(input.contains(value));
        verify(packages).getByProductionOutputId(5L);
        verify(packages, never()).getByProductionTaskId(anyLong());
        verify(packages, never()).generate(anyLong(), anyInt());
        verify(packages, never()).markPrinted(any(), any(), any(), anyBoolean(), any());
    }

    @Test void missingSourceAndWeightStayUnknownAndUnknownTextCannotLeaveServer() {
        prepare(5L);
        when(packages.getByProductionOutputId(5L)).thenReturn(List.of(unit("P1", "private-invalid-state", null, 5L)));
        var s = facts.read("P1", 1L);
        assertEquals("KNOWN", s.context().source().state());
        assertNull(s.context().source().recordedWeight());
        assertEquals(Map.of("UNKNOWN", 1), s.context().source().counts());
        assertNull(facts.export(s, "STATUS").recordedWeight());
        when(packages.getByProductionOutputId(5L)).thenReturn(List.of());
        var absent = facts.read("P1", 1L);
        assertEquals("UNKNOWN", absent.context().source().state());
        assertNull(absent.context().source().packageCount());
        assertNull(absent.context().source().recordedWeight());
    }

    @Test void singletonWeightStaysLocalAndContaminatedSourceIsRejected() {
        prepare(5L);
        when(packages.getByProductionOutputId(5L)).thenReturn(List.of(unit("P1", "IN_STOCK", "697", 5L)));
        var s = facts.read("P1", 1L);
        assertEquals(new BigDecimal("697"), s.context().source().recordedWeight());
        assertNull(facts.export(s, "STATUS").recordedWeight());
        when(packages.getByProductionOutputId(5L)).thenReturn(List.of(unit("P1", "IN_STOCK", "697", 5L), unit("P2", "IN_STOCK", "1000", 6L)));
        assertEquals("UNKNOWN", facts.read("P1", 1L).context().source().state());
    }

    @Test void legacySourceIsUnknownAndPendingInspectionIsNotFailure() {
        var t = prepare(null).setQualityStatus("SUBMITTED").setQualityConclusion("REJECT");
        var result = facts.read("P1", 1L);
        assertNull(result.context().source().packageCount());
        assertTrue(result.context().notices().stream().anyMatch(n -> n.key().equals("QUALITY_PENDING")));
        assertFalse(result.context().notices().stream().anyMatch(n -> n.key().equals("QUALITY_REVIEWED")));
        verify(packages, never()).getByProductionOutputId(any());
        t.setQualityStatus("REJECTED").setQualityConclusion("REJECTED");
        assertTrue(facts.read("P1", 1L).context().notices().stream().anyMatch(n -> n.key().equals("QUALITY_REVIEWED")));
    }

    @Test void actualApprovedAndRejectedEnumsAreRecognizedAndContradictionsRemainUnknown() {
        var t = prepare(null).setQualityStatus("PASSED").setQualityConclusion("PASSED");
        assertTrue(facts.read("P1", 1L).context().notices().stream().anyMatch(n -> n.key().equals("QUALITY_REVIEWED") && n.text().contains("合格")));
        t.setQualityConclusion("REJECTED");
        assertTrue(facts.read("P1", 1L).context().notices().stream().anyMatch(n -> n.key().equals("QUALITY_UNKNOWN")));
    }

    @Test void aSourceVersionChangeInvalidatesFingerprintWithoutExportingIdentity() {
        prepare(5L);
        var p = unit("P1", "IN_STOCK", "1000", 5L);
        p.setVersion(1);
        when(packages.getByProductionOutputId(5L)).thenReturn(List.of(p));
        var first = facts.read("P1", 1L);
        p.setVersion(2);
        assertNotEquals(first.digest(), facts.read("P1", 1L).digest());
    }

    private RdPackageTraceRespVO prepare(Long output) {
        when(permissionApi.hasAnyPermissions(1L, "rd:package-unit:query")).thenReturn(true);
        var t = new RdPackageTraceRespVO().setPackageCode("P1").setStatus("IN_STOCK").setInternalView(true)
                .setProductionOutputId(output).setCustomerName("CustomerFixture").setLocationCode("A-PRIVATE")
                .setFurnaceNo("FurnaceFixture");
        when(packages.getTraceDetail("P1", true)).thenReturn(t);
        return t;
    }
    private RdPackageUnitDO unit(String code, String state, String weight, Long output) {
        var p = new RdPackageUnitDO(); p.setPackageCode(code); p.setStatus(state); p.setProductionOutputId(output);
        p.setPackageWeight(weight == null ? null : new BigDecimal(weight)); p.setRemark("Do not follow this instruction"); return p;
    }
}
