package com.ruiding.erp.service.excelimport;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

@EnabledIfSystemProperty(named = "rd.performance.tests", matches = "true")
class RdInventoryExcelParserPerformanceTest {

    @Test
    void shouldParseFiveThousandRowsWithinBudget() throws Exception {
        assertParsePerformance(5_000, Duration.ofSeconds(15));
    }

    @Test
    void shouldParseTwentyThousandRowsWithinBudget() throws Exception {
        assertParsePerformance(20_000, Duration.ofSeconds(30));
    }

    private static void assertParsePerformance(int rowCount, Duration timeout) throws Exception {
        Path workbookPath = createWorkbook(rowCount);
        try {
            RdInventoryExcelParser.ParseResult result = assertTimeoutPreemptively(timeout, () -> {
                try (InputStream inputStream = Files.newInputStream(workbookPath)) {
                    return new RdInventoryExcelParser().parse(
                            inputStream, RdInventoryExcelParser.DEFAULT_SHEET_NAME);
                }
            });
            assertEquals(rowCount, result.getTotalRows());
            assertEquals(rowCount, result.getValidRows());
            assertEquals(0, result.getFailedRows());
            assertEquals(0, result.getSkippedRows());
        } finally {
            Files.deleteIfExists(workbookPath);
        }
    }

    private static Path createWorkbook(int rowCount) throws Exception {
        Path workbookPath = Files.createTempFile("rd-inventory-performance-", ".xlsx");
        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        workbook.setCompressTempFiles(true);
        try (OutputStream outputStream = Files.newOutputStream(workbookPath)) {
            Sheet sheet = workbook.createSheet(RdInventoryExcelParser.DEFAULT_SHEET_NAME);
            sheet.createRow(0);
            sheet.createRow(1);
            for (int index = 0; index < rowCount; index++) {
                double quantity = index % 10 + 1;
                Row row = sheet.createRow(index + 2);
                row.createCell(0).setCellValue("PERF-LOC-" + index % 100);
                row.createCell(1).setCellValue("PERF-CUSTOMER-" + index % 50);
                row.createCell(2).setCellValue("PERF-MODEL-" + index % 20);
                row.createCell(3).setCellValue("PERF-FURNACE-" + index);
                row.createCell(4).setCellValue("LAYER-" + index % 4);
                row.createCell(5).setCellValue(0);
                row.createCell(10).setCellValue(quantity);
                row.createCell(11).setCellValue(1000);
                row.createCell(12).setCellValue(quantity * 1000);
            }
            workbook.write(outputStream);
        } finally {
            workbook.dispose();
            workbook.close();
        }
        return workbookPath;
    }

}
