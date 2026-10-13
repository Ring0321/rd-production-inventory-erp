package com.ruiding.erp.service.packageunit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RdDigitalLinkServiceTest {

    @Test
    void shouldBuildCompanyOwnedPackageUrl() {
        RdDigitalLinkService service = new RdDigitalLinkService("https://id.tjldsic.com/pkg/");

        assertEquals("https://id.tjldsic.com/pkg/LD20260729A0035",
                service.build("LD20260729A0035"));
    }

    @Test
    void shouldRejectNonHttpBaseUrl() {
        assertThrows(IllegalArgumentException.class,
                () -> new RdDigitalLinkService("javascript:alert(1)"));
    }

    @Test void malformedOrCredentialBearingBaseAddressesAreRejected() {
        for (String base : java.util.List.of("http:///pkg", "https://user:fixture@example.test/pkg",
                "https://example.test/pkg?private=fixture", "https://example.test/pkg#fragment",
                "https://example.test/../pkg", "https://example.test/%2e%2e/pkg",
                "https://example.test:70000/pkg", "https://example.test/other")) {
            assertThrows(IllegalArgumentException.class, () -> new RdDigitalLinkService(base), base);
        }
    }

    @Test void packageIdentityMustBeOneBoundedPathSegment() {
        var links = new RdDigitalLinkService("http://localhost:9000/pkg");
        for (String code : java.util.List.of("../P1", "P1/P2", "P1?private=fixture", "P1#x", "P 1", "A".repeat(65))) {
            assertThrows(IllegalArgumentException.class, () -> links.build(code), code);
        }
        assertEquals("http://localhost:9000/pkg/P1", links.build(" P1 "));
    }

    @Test void originalIdentityAndCurrentResourceAddressesAreSeparate() {
        var links = new RdDigitalLinkService("http://localhost:9000/pkg");
        String original = "https://id.tjldsic.com/pkg/P1";
        assertEquals(original, RdDigitalLinkService.checkedIdentity("P1", original));
        assertEquals("http://localhost:9000/pkg/P1?resource=passport", links.representation("P1", "passport"));
        assertThrows(IllegalArgumentException.class, () -> RdDigitalLinkService.checkedIdentity("P2", original));
        assertThrows(IllegalArgumentException.class, () -> RdDigitalLinkService.checkedIdentity("P1", original + "?internal=1"));
        assertThrows(IllegalArgumentException.class, () -> links.representation("P1", "https://other.test"));
    }

    @Test void newPrefixMustBeRoutableWithoutChangingHistoricalIdentity() {
        for (String base : java.util.List.of("https://example.test/factory.v1/pkg",
                "https://example.test/%66actory/pkg", "https://example.test/a//pkg")) {
            assertThrows(IllegalArgumentException.class, () -> new RdDigitalLinkService(base));
        }
        var links = new RdDigitalLinkService("https://example.test/factory_1/site-2/pkg");
        assertEquals("https://example.test/factory_1/site-2/pkg/P1", links.build("P1"));
        String historical = "https://old.example.test/factory.v1/pkg/P1";
        assertEquals(historical, RdDigitalLinkService.checkedIdentity("P1", historical));
    }
}
