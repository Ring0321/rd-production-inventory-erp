package com.ruiding.erp.service.documentocr;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RdDocumentOcrAccessTest {
    private final PermissionCommonApi permissions = mock(PermissionCommonApi.class);
    private final RdDocumentOcrAccess access = new RdDocumentOcrAccess(permissions);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void login() {
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(42L).setUserType(2), new MockHttpServletRequest());
    }

    @Test void unauthenticatedNeverTouchesPermissionOrOcrService() {
        assertFalse(access.allowed());
        verifyNoInteractions(permissions);
    }

    @Test void driverIsDeniedEvenWithBroadCreatePermissions() {
        login();
        when(permissions.hasAnyRoles(42L, "rd_forklift_driver")).thenReturn(true);
        assertFalse(access.allowed());
        verify(permissions, never()).hasAnyPermissions(anyLong(), any(String[].class));
    }

    @Test void originalCreatePermissionsAreRequiredAsAnOr() {
        login();
        assertFalse(access.allowed());
        when(permissions.hasAnyPermissions(42L, "rd:pickup-slip:create", "rd:quality-inspection:create")).thenReturn(true);
        assertTrue(access.allowed());
    }
}
