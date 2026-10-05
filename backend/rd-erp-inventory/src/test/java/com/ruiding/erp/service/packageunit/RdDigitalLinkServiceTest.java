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
}
