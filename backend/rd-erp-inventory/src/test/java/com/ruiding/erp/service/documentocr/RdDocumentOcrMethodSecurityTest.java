package com.ruiding.erp.service.documentocr;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import com.ruiding.erp.controller.admin.documentocr.RdDocumentOcrController;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RdDocumentOcrMethodSecurityTest {
    @Configuration
    @EnableGlobalMethodSecurity(prePostEnabled = true, proxyTargetClass = true)
    static class Config {
        @Bean PermissionCommonApi permissionApi() { return mock(PermissionCommonApi.class); }
        @Bean RdDocumentOcrService service() { return mock(RdDocumentOcrService.class); }
        @Bean("rdDocumentOcrAccess") RdDocumentOcrAccess access(PermissionCommonApi api) { return new RdDocumentOcrAccess(api); }
        @Bean RdDocumentOcrController controller(RdDocumentOcrService service) { return new RdDocumentOcrController(service); }
    }

    @Test void actualMethodSecurityStopsDriverAndReadOnlyActorsBeforeServiceInvocation() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var api = context.getBean(PermissionCommonApi.class);
            var service = context.getBean(RdDocumentOcrService.class);
            var controller = context.getBean(RdDocumentOcrController.class);
            SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(42L).setUserType(2), new MockHttpServletRequest());
            assertThrows(AccessDeniedException.class, controller::status);
            assertThrows(AccessDeniedException.class, () -> controller.recognize(null, null));
            verifyNoInteractions(service);
            when(api.hasAnyPermissions(42L, "rd:pickup-slip:create", "rd:quality-inspection:create")).thenReturn(true);
            assertDoesNotThrow(controller::status);
            reset(service);
            when(api.hasAnyRoles(42L, "rd_forklift_driver")).thenReturn(true);
            assertThrows(AccessDeniedException.class, controller::status);
            assertThrows(AccessDeniedException.class, () -> controller.recognize(null, null));
            verifyNoInteractions(service);
        } finally { SecurityContextHolder.clearContext(); }
    }
}
