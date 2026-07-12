package com.ruiding.erp.service.packageunit;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class RdPackageCodeGenerator {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    public String generate(LocalDate date, int sequence) {
        if (sequence < 1 || sequence > 999) {
            throw new IllegalArgumentException("Daily package sequence must be between 1 and 999");
        }
        return date.format(DATE_FORMAT) + String.format("%03d", sequence);
    }
}
