package com.ruiding.erp.service.weight;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import com.ruiding.erp.controller.admin.weight.RdWeightAssistantController;
import com.ruiding.erp.controller.admin.weight.vo.RdWeightSuggestReqVO;
import com.ruiding.erp.dal.dataobject.loading.RdPickupSlipDO;
import com.ruiding.erp.dal.dataobject.loading.RdPickupSlipLineDO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.ruiding.erp.service.weight.RdWeightSolverTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RdWeightAssistantServiceTest {
    @Mock PermissionService permissionService;
    @Mock RdWeightInventoryReader reader;
    @Mock RdWeightSolver solver;
    @InjectMocks RdWeightAssistantService service;

    @BeforeEach void tenant() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @Test void requiresAllThreePermissionsBeforeReadingFacts() {
        when(permissionService.hasAnyPermissions(7L, "rd:pickup-slip:query")).thenReturn(true);
        assertThrows(ServiceException.class, () -> service.suggest(request(), 7L));
        verifyNoInteractions(reader, solver);
    }

    @Test void rejectsDriverEvenWithLegacyWildcardGrants() {
        when(permissionService.hasAnyRoles(7L, "rd_forklift_driver")).thenReturn(true);
        assertThrows(ServiceException.class, () -> service.suggest(request(), 7L));
        verifyNoInteractions(reader, solver);
    }

    @Test void rejectsUnauthenticatedAndUnknownTenant() {
        assertThrows(ServiceException.class, () -> service.suggest(request(), null));
        allow();
        TenantContextHolder.clear();
        assertThrows(ServiceException.class, () -> service.suggest(request(), 7L));
        verifyNoInteractions(reader, solver);
    }

    @Test void requiresExactLineForMixedModelAndRejectsForeignLine() {
        allow();
        when(reader.readSlip(1L)).thenReturn(slip());
        when(reader.readLines(1L)).thenReturn(List.of(line(11L), line(12L)));
        var request = request();
        request.setPickupSlipLineId(null);
        assertThrows(ServiceException.class, () -> service.suggest(request, 7L));
        request.setPickupSlipLineId(99L);
        assertThrows(ServiceException.class, () -> service.suggest(request, 7L));
        verifyNoInteractions(solver);
    }

    @Test void constrainsTargetPlusToleranceToAuthorizedLineNotWholeSlip() {
        setupSlip();
        var req = request();
        req.setTargetWeightKg(kg("999"));
        req.setToleranceKg(kg("2"));
        assertThrows(ServiceException.class, () -> service.suggest(req, 7L));
        verifyNoInteractions(solver);
    }

    @Test void rejectsCountOnlyAuthorizationAndExcessPrecision() {
        setupSlip();
        var line = line(11L);
        line.setRequestedWeight(null);
        when(reader.readLines(1L)).thenReturn(List.of(line));
        assertThrows(ServiceException.class, () -> service.suggest(request(), 7L));
        var req = request();
        req.setTargetWeightKg(kg("0.0001"));
        assertThrows(ServiceException.class, () -> service.suggest(req, 7L));
        verifyNoInteractions(solver);
    }

    @Test void refusesToSplitAnAlreadyAssignedWholeTruck() {
        setupSlip();
        when(reader.hasTask(1L)).thenReturn(true);
        assertThrows(ServiceException.class, () -> service.suggest(request(), 7L));
        verifyNoInteractions(solver);
    }

    @Test void reportsCandidateBudgetWithoutSolvingTruncatedInventory() {
        setupSlip();
        when(reader.readCandidates(any(), any())).thenReturn(
                new RdWeightInventoryReader.Inventory(List.of(), Map.of(), 501, false));
        var result = service.suggest(request(), 7L);
        assertEquals("BUDGET_EXCEEDED", result.getSolverStatus());
        assertFalse(result.isCandidateScanComplete());
        assertEquals(501, result.getScannedCount());
        assertNull(result.getCandidateCount());
        assertTrue(result.getBags().isEmpty());
        verifyNoInteractions(solver);
    }

    @Test void returnsServerFactsAndGuaranteedInventoryShortfall() {
        setupSlip();
        var inventory = new RdWeightInventoryReader.Inventory(List.of(bag(1, "400")), Map.of(10L, 1), 1, true);
        when(reader.readCandidates(any(), any())).thenReturn(inventory);
        when(solver.solve(anyList(), anyMap(), anyInt(), any(), any(), anyDouble()))
                .thenReturn(new RdWeightSolver.Result("INFEASIBLE", List.of(), null, null));
        var result = service.suggest(request(), 7L);
        assertEquals(0, result.getShortfallKg().compareTo(kg("600")));
        assertNull(result.getTotalWeightKg());
        assertEquals(11L, result.getPickupSlipLineId());
        assertEquals("CUSTOMER", result.getCustomerName());
        assertFalse(result.isWithinTolerance());
    }

    @Test void feasibleIncumbentIsNotRelabeledOptimal() {
        setupSlip();
        when(reader.readCandidates(any(), any())).thenReturn(
                new RdWeightInventoryReader.Inventory(List.of(bag(1, "1000")), Map.of(10L, 1), 1, true));
        when(solver.solve(anyList(), anyMap(), anyInt(), any(), any(), anyDouble()))
                .thenReturn(new RdWeightSolver.Result("FEASIBLE", List.of(bag(1, "1000")), kg("1000"), 0.0));
        var result = service.suggest(request(), 7L);
        assertEquals("FEASIBLE", result.getSolverStatus());
        assertTrue(result.isWithinTolerance());
        assertTrue(result.getMessage().contains("未证明最优"));
    }

    @Test void saturatedConcurrencyBudgetDoesNotReadOrSolveCandidates() throws Exception {
        setupSlip();
        var field = RdWeightAssistantService.class.getDeclaredField("slots");
        field.setAccessible(true);
        var slots = (java.util.concurrent.Semaphore) field.get(service);
        slots.acquire(2);
        try {
            assertEquals("BUSY", service.suggest(request(), 7L).getSolverStatus());
            verify(reader, never()).readCandidates(any(), any());
            verifyNoInteractions(solver);
        } finally { slots.release(2); }
    }

    @Test void nativeLinkageFailureIsExplicitlyUnavailable() {
        setupSlip();
        when(reader.readCandidates(any(), any())).thenReturn(
                new RdWeightInventoryReader.Inventory(List.of(bag(1, "1000")), Map.of(10L, 1), 1, true));
        when(solver.solve(anyList(), anyMap(), anyInt(), any(), any(), anyDouble()))
                .thenThrow(new UnsatisfiedLinkError("test native unavailable"));
        var result = service.suggest(request(), 7L);
        assertEquals("UNAVAILABLE", result.getSolverStatus());
        assertTrue(result.getBags().isEmpty());
    }

    @Test void controllerAndDtoExposeOnlyReadOnlySuggestionContract() throws Exception {
        String expression = RdWeightAssistantController.class.getMethod("suggest", RdWeightSuggestReqVO.class)
                .getAnnotation(PreAuthorize.class).value();
        for (String permission : List.of("rd:pickup-slip:query", "rd:package-unit:query", "rd:loading-task:dispatch"))
            assertTrue(expression.contains(permission));
        assertTrue(expression.contains(" and "));
        assertTrue(RdWeightAssistantService.class.getMethod("suggest", RdWeightSuggestReqVO.class, Long.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class).readOnly());
        assertEquals(List.of("pickupSlipId", "pickupSlipLineId", "targetWeightKg", "toleranceKg"),
                Arrays.stream(RdWeightSuggestReqVO.class.getDeclaredFields()).map(java.lang.reflect.Field::getName).toList());
    }

    @Test void noSolutionSerializesExplicitNullWeightsEvenWithGlobalNonNullDefaults() {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
        com.fasterxml.jackson.databind.JsonNode json = mapper.valueToTree(
                new com.ruiding.erp.controller.admin.weight.vo.RdWeightSuggestRespVO().setSolverStatus("UNKNOWN"));
        assertTrue(json.has("totalWeightKg"));
        assertTrue(json.get("totalWeightKg").isNull());
        assertTrue(json.get("shortfallKg").isNull());
    }

    private void allow() {
        for (String permission : List.of("rd:pickup-slip:query", "rd:package-unit:query", "rd:loading-task:dispatch"))
            lenient().when(permissionService.hasAnyPermissions(7L, permission)).thenReturn(true);
    }
    private void setupSlip() {
        allow();
        when(reader.readSlip(1L)).thenReturn(slip());
        when(reader.readLines(1L)).thenReturn(List.of(line(11L)));
    }
    static RdWeightSuggestReqVO request() {
        return new RdWeightSuggestReqVO().setPickupSlipId(1L).setPickupSlipLineId(11L)
                .setTargetWeightKg(kg("1000")).setToleranceKg(kg("0"));
    }
    static RdPickupSlipDO slip() {
        var value = new RdPickupSlipDO().setId(1L).setSlipNo("SLIP-1").setStatus("ISSUED")
                .setCustomerName("CUSTOMER").setRequestedWeight(kg("2000"));
        value.setTenantId(1L);
        return value;
    }
    static RdPickupSlipLineDO line(long id) {
        var value = new RdPickupSlipLineDO().setId(id).setSlipId(1L).setProductModel("MODEL")
                .setRequestedWeight(kg("1000")).setRequestedPackageCount(2);
        value.setTenantId(1L);
        return value;
    }
}
