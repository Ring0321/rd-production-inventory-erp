package com.ruiding.erp.controller.admin.packageunit;

import com.ruiding.erp.service.packageunit.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.LinkedMultiValueMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RdPackageLinkControllerTest {
    @Mock RdPackagePassportService passports;
    @Spy RdDigitalLinkService links = new RdDigitalLinkService("http://localhost:9000/pkg");
    @InjectMocks RdPackageLinkController controller;
    @Test void mediaSelectionRespectsWeightsWildcardsAndExclusions() {
        assertEquals("web", RdPackageLinkController.selectRepresentation(null));
        assertEquals("passport", RdPackageLinkController.selectRepresentation("text/html;q=0.2,application/json;q=0.9"));
        assertEquals("web", RdPackageLinkController.selectRepresentation("application/json;q=0,*/*;q=0.8"));
        assertEquals("links", RdPackageLinkController.selectRepresentation("application/linkset+json"));
        assertNull(RdPackageLinkController.selectRepresentation("image/png"));
    }
    @Test void pageResolutionOnlyUsesConfiguredOriginAndPreservesAllowedWorkMode() {
        var query = new LinkedMultiValueMap<String,String>();
        query.add("workMode", "pda"); query.add("next", "https://attacker.test");
        var r = controller.resolve("P1", query, "text/html", null, null);
        assertEquals(302, r.getStatusCodeValue());
        assertEquals("http://localhost:9000/pkg/P1?resource=web&workMode=pda", r.getHeaders().getLocation().toString());
        verifyNoInteractions(passports);
        query.add("resource", "web"); query.add("resource", "passport");
        assertEquals(400, controller.resolve("P1", query, "text/html", null, null).getStatusCodeValue());
    }
    @Test void nginxWebTransportStillValidatesSelectorsBeforeServingThePage() {
        var query = new LinkedMultiValueMap<String,String>();
        query.add("resource", "web");
        var page = controller.resolve("P1", query, "application/json", null, "nginx");
        assertEquals(200, page.getStatusCodeValue());
        assertEquals("/_rd-package-web", page.getHeaders().getFirst("X-Accel-Redirect"));
        assertNull(page.getHeaders().getLocation());
        query.add("resource", "passport");
        var invalid = controller.resolve("P1", query, "text/html", null, "nginx");
        assertEquals(400, invalid.getStatusCodeValue());
        assertNull(invalid.getHeaders().getFirst("X-Accel-Redirect"));
        verifyNoInteractions(passports);
    }
    @Test void publicRepresentationsHaveFixedTypesAndConditionalRequestsReturnNoBody() {
        var p = new RdPackagePassportService.Passport("DUOXU_PUBLIC_PACKAGE_V1", "SHA-256", "a".repeat(64), "2026-10-13T03:00:00Z", "{}");
        when(passports.read("P1")).thenReturn(new RdPackagePassportService.Resources(p, Map.of("packageCode", "P1"), Map.of("linkset", java.util.List.of())));
        var snapshot = controller.resource("P1", "passport", null);
        assertEquals("application/json", snapshot.getHeaders().getContentType().getType()+"/"+snapshot.getHeaders().getContentType().getSubtype());
        assertEquals("no-store", snapshot.getHeaders().getCacheControl());
        assertEquals(304, controller.resource("P1", "passport", snapshot.getHeaders().getETag()).getStatusCodeValue());
        assertNull(controller.resource("P1", "passport", snapshot.getHeaders().getETag()).getBody());
        var document = controller.resource("P1", "document", snapshot.getHeaders().getETag());
        assertEquals(200, document.getStatusCodeValue());
        assertNotEquals(snapshot.getHeaders().getETag(), document.getHeaders().getETag());
        assertEquals(304, controller.resource("P1", "document", document.getHeaders().getETag()).getStatusCodeValue());
        var directory = controller.resource("P1", "links", null);
        assertNotEquals(snapshot.getHeaders().getETag(), directory.getHeaders().getETag());
        assertEquals(404, controller.resource("P1", "private", null).getStatusCodeValue());
    }
}
