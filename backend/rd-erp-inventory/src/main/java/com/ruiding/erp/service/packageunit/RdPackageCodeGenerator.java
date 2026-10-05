package com.ruiding.erp.service.packageunit;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class RdPackageCodeGenerator {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String COMPANY_PREFIX = "LD";
    private static final String SERIES_CODE = "A";

    public String generate(LocalDate date, int sequence) {
        if (sequence < 1 || sequence > 9999) {
            throw new IllegalArgumentException("Daily package sequence must be between 1 and 9999");
        }
        return COMPANY_PREFIX + date.format(DATE_FORMAT) + SERIES_CODE + String.format("%04d", sequence);
    }
}
