package com.ruiding.erp.service.packageunit;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RdPackageCodeGeneratorTest {

    @Test
    void shouldGenerateOneHundredUniqueCodesForSameDate() {
        RdPackageCodeGenerator generator = new RdPackageCodeGenerator();
        Set<String> codes = new HashSet<>();
        for (int sequence = 1; sequence <= 100; sequence++) {
            codes.add(generator.generate(LocalDate.of(2026, 7, 12), sequence));
        }
        assertEquals(100, codes.size());
        assertEquals("LD20260712A0001", generator.generate(LocalDate.of(2026, 7, 12), 1));
        assertEquals("LD20260712A0100", generator.generate(LocalDate.of(2026, 7, 12), 100));
    }
}
