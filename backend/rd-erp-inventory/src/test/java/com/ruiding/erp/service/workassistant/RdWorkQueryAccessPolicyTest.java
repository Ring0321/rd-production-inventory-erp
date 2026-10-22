package com.ruiding.erp.service.workassistant;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static com.ruiding.erp.service.workassistant.RdWorkQueryContract.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RdWorkQueryAccessPolicyTest {
    @Mock PermissionService permissions;
    @InjectMocks RdWorkQueryAccessPolicy policy;
    private LoginUser user;

    @BeforeEach void login() {
        TenantContextHolder.setTenantId(1L);
        user = new LoginUser().setId(7L).setTenantId(1L).setUserType(UserTypeEnum.ADMIN.getValue());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @AfterEach void clear() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test void rejectsMissingUnauthenticatedAndNonAdminIdentityBeforePermissions() {
        SecurityContextHolder.clearContext();
        assertDenied(Query.Q03);
        login();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null));
        assertDenied(Query.Q03);
        login();
        user.setId(0L);
        assertDenied(Query.Q03);
        user.setId(7L).setUserType(UserTypeEnum.MEMBER.getValue());
        assertDenied(Query.Q03);
        verifyNoInteractions(permissions);
    }

    @Test void rejectsMissingIgnoredForeignAndCrossVisitTenant() {
        TenantContextHolder.clear();
        assertDenied(Query.Q03);
        TenantContextHolder.setTenantId(2L);
        assertDenied(Query.Q03);
        TenantContextHolder.setTenantId(1L);
        TenantContextHolder.setIgnore(true);
        assertDenied(Query.Q03);
        TenantContextHolder.setIgnore(false);
        user.setVisitTenantId(2L);
        assertDenied(Query.Q03);
        verifyNoInteractions(permissions);
    }

    @ParameterizedTest @EnumSource(value = Query.class, names = {"Q01", "Q02", "Q03", "Q04", "Q05", "Q06", "Q07", "Q08"})
    void requiresOriginalSourceCapabilitiesNotJustMenuVisibilityOrRoleNames(Query query) {
        assertDenied(query);
        for (String permission : query.permissions()) {
            when(permissions.hasAnyPermissions(7L, permission)).thenReturn(true);
        }
        var scope = policy.authorize(request(query));
        assertEquals(1L, scope.tenantId());
        assertEquals(7L, scope.userId());
        assertEquals(DataScope.TENANT_AUTHORIZED, scope.dataScope());
    }

    @Test void loadingClaimsRequireBothTaskAndPackageReadRights() {
        when(permissions.hasAnyPermissions(7L, "rd:loading-task:query")).thenReturn(true);
        assertDenied(Query.Q08);
        when(permissions.hasAnyPermissions(7L, "rd:package-unit:query")).thenReturn(true);
        assertNotNull(policy.authorize(request(Query.Q08)));
    }

    @ParameterizedTest @EnumSource(value = Query.class, names = {"Q01", "Q02", "Q03", "Q04", "Q05", "Q08"})
    void driverCannotAcquireGlobalFactsEvenWithLegacyWildcardRights(Query query) {
        when(permissions.hasAnyRoles(7L, "rd_forklift_driver")).thenReturn(true);
        lenient().when(permissions.hasAnyPermissions(eq(7L), anyString())).thenReturn(true);
        assertDenied(query);
        verify(permissions, never()).hasAnyPermissions(anyLong(), anyString());
    }

    @ParameterizedTest @EnumSource(value = Query.class, names = {"Q06", "Q07"})
    void driverReceivesOwnTaskScopeAndCannotReadAnotherDriverOrTenant(Query query) {
        when(permissions.hasAnyRoles(7L, "rd_forklift_driver")).thenReturn(true);
        String execute = query == Query.Q06 ? "rd:putaway-task:execute" : "rd:loading-task:execute";
        assertDenied(query);
        when(permissions.hasAnyPermissions(7L, execute)).thenReturn(true);
        var scope = policy.authorize(request(query));
        assertEquals(DataScope.OWN_TASKS, scope.dataScope());
        assertDoesNotThrow(() -> scope.requireSource(1L, 7L));
        assertThrows(ServiceException.class, () -> scope.requireSource(1L, 8L));
        assertThrows(ServiceException.class, () -> scope.requireSource(2L, 7L));
        assertThrows(ServiceException.class, () -> scope.requireSource(1L, null));
    }

    @ParameterizedTest @EnumSource(value = Query.class, names = {"Q06", "Q07"})
    void driverWithWildcardRightsStillReceivesOnlyOwnTaskScope(Query query) {
        when(permissions.hasAnyRoles(7L, "rd_forklift_driver")).thenReturn(true);
        when(permissions.hasAnyPermissions(eq(7L), anyString())).thenReturn(true);
        var scope = policy.authorize(request(query));
        assertEquals(DataScope.OWN_TASKS, scope.dataScope());
        assertThrows(ServiceException.class, () -> scope.requireSource(1L, 8L));
    }

    @Test void executorWithoutGlobalReadUsesOwnTaskScopeEvenWithoutDriverRole() {
        when(permissions.hasAnyPermissions(7L, "rd:loading-task:query")).thenReturn(false);
        when(permissions.hasAnyPermissions(7L, "rd:loading-task:execute")).thenReturn(true);
        assertEquals(DataScope.OWN_TASKS, policy.authorize(request(Query.Q07)).dataScope());
    }

    @Test void authorizedScopeCannotBeReusedUnderChangedIdentityOrTenant() {
        when(permissions.hasAnyPermissions(7L, "rd:package-unit:query")).thenReturn(true);
        var scope = policy.authorize(request(Query.Q03));
        assertDoesNotThrow(() -> scope.requireSource(1L, null));
        assertThrows(ServiceException.class, () -> scope.requireSource(2L, null));
        user.setId(8L);
        assertThrows(ServiceException.class, () -> scope.requireSource(1L, null));
        user.setId(7L);
        TenantContextHolder.setIgnore(true);
        assertThrows(ServiceException.class, () -> scope.requireSource(1L, null));
    }

    @ParameterizedTest @EnumSource(value = Query.class, names = {"Q01", "Q02", "Q03", "Q04", "Q05", "Q06", "Q07", "Q08"})
    void unsupportedWarehouseScopeNeverFallsBackToGlobal(Query query) {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> policy.authorize(new Request(query, "DEMO-SPACE-V2", null, 1, 20)));
        assertEquals(400, exception.getCode());
        verifyNoInteractions(permissions);
    }

    @ParameterizedTest @EnumSource(value = Query.class, names = {"Q09", "Q10"})
    void futureAlertsStayUnavailableEvenForPrivilegedAccounts(Query query) {
        ServiceException exception = assertThrows(ServiceException.class, () -> policy.authorize(request(query)));
        assertEquals(503, exception.getCode());
        verifyNoInteractions(permissions);
    }

    private void assertDenied(Query query) {
        assertEquals(403, assertThrows(ServiceException.class, () -> policy.authorize(request(query))).getCode());
    }

    private static Request request(Query query) {
        return new Request(query, null, query == Query.Q10 ? 1L : null, 1, 20);
    }
}
