package com.ruiding.erp.service.excelimport;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RdInventoryExcelRealFileTest {

    @Test
    void shouldMatchIndependentlyAuditedRealWorkbookSample() throws Exception {
        String workbookPath = System.getProperty("rd.realWorkbook");
        Assumptions.assumeTrue(workbookPath != null && !workbookPath.trim().isEmpty());

        RdInventoryExcelParser.ParseResult result;
        try (InputStream inputStream = Files.newInputStream(Paths.get(workbookPath))) {
            result = new RdInventoryExcelParser().parse(
                    inputStream, RdInventoryExcelParser.DEFAULT_SHEET_NAME);
        }

        assertEquals(455, result.getTotalRows());
        assertEquals(306, result.getValidRows());
        assertEquals(4, result.getFailedRows());
        assertEquals(145, result.getSkippedRows());

        List<RdInventoryExcelParser.ParsedRow> firstFifty = result.getRows().subList(0, 50);
        assertEquals(3, firstFifty.get(0).getRowNo());
        assertEquals(54, firstFifty.get(49).getRowNo());
        assertEquals(34, countStatus(firstFifty, RdInventoryExcelParser.ROW_STATUS_VALID));
        assertEquals(16, countStatus(firstFifty, RdInventoryExcelParser.ROW_STATUS_SKIPPED));
        assertEquals("3-23", firstFifty.get(0).getLocationCode());
        assertEquals("瑞鼎", firstFifty.get(0).getCustomerName());
        assertEquals("041", firstFifty.get(0).getProductModel());

        List<Integer> errorRows = result.getRows().stream()
                .filter(row -> RdInventoryExcelParser.ROW_STATUS_ERROR.equals(row.getStatus()))
                .map(RdInventoryExcelParser.ParsedRow::getRowNo)
                .collect(Collectors.toList());
        assertEquals(Arrays.asList(396, 397, 398, 399), errorRows);
    }

    private static long countStatus(List<RdInventoryExcelParser.ParsedRow> rows, String status) {
        return rows.stream().filter(row -> status.equals(row.getStatus())).count();
    }

}
