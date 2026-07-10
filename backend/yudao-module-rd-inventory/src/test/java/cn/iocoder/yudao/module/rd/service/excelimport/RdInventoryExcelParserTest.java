package cn.iocoder.yudao.module.rd.service.excelimport;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RdInventoryExcelParserTest {

    @Test
    void shouldParseCurrentStockAndReportInvalidRows() throws Exception {
        byte[] workbook = createWorkbook();

        RdInventoryExcelParser.ParseResult result = new RdInventoryExcelParser().parse(
                new ByteArrayInputStream(workbook), RdInventoryExcelParser.DEFAULT_SHEET_NAME);

        assertEquals(4, result.getTotalRows());
        assertEquals(1, result.getValidRows());
        assertEquals(1, result.getSkippedRows());
        assertEquals(2, result.getFailedRows());

        RdInventoryExcelParser.ParsedRow valid = result.getRows().get(0);
        assertEquals(3, valid.getRowNo());
        assertEquals("3-23", valid.getLocationCode());
        assertEquals("13031", valid.getProductModel());
        assertEquals(0, valid.getWarehouseQty().compareTo(decimal("2")));
        assertEquals(0, valid.getStockWeight().compareTo(decimal("2000")));
        assertEquals(RdInventoryExcelParser.ROW_STATUS_VALID, valid.getStatus());

        RdInventoryExcelParser.ParsedRow missingLocation = result.getRows().get(2);
        assertEquals(5, missingLocation.getRowNo());
        assertEquals(RdInventoryExcelParser.ROW_STATUS_ERROR, missingLocation.getStatus());
        assertTrue(missingLocation.getErrors().stream()
                .anyMatch(error -> "locationCode".equals(error.getFieldName())));

        RdInventoryExcelParser.ParsedRow weightMismatch = result.getRows().get(3);
        assertEquals(RdInventoryExcelParser.ROW_STATUS_ERROR, weightMismatch.getStatus());
        assertTrue(weightMismatch.getErrors().stream()
                .anyMatch(error -> "stockWeight".equals(error.getFieldName())));
    }

    private static byte[] createWorkbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(RdInventoryExcelParser.DEFAULT_SHEET_NAME);
            createHeader(sheet);
            createDataRow(sheet, 2, "3-23", "贝特瑞产品一部", "13031", "1-13#27", "下层【X】",
                    1, 1, 0, 0, 0, 1000, 2000);
            createDataRow(sheet, 3, "3-22", "瑞鼎", "035", "1-06#25", "中层【Z】",
                    0, 0, 0, 0, 0, 1000, 0);
            createDataRow(sheet, 4, null, "湖北皇恩烨", "S11石墨化-混011&02", "2-10#16", "上层【S】",
                    0, 0, 34, 0, 0, 1000, 34000);
            createDataRow(sheet, 5, "3-24", "瑞鼎", "041", "1-03#18", "下层【X】",
                    1, 0, 0, 0, 0, 1000, 999);

            Row total = sheet.createRow(6);
            total.createCell(4).setCellValue("破碎料");
            total.createCell(10).setCellValue(199);
            Row sectionTotal = sheet.createRow(7);
            sectionTotal.createCell(0).setCellValue("成品料");
            sectionTotal.createCell(12).setCellValue(164606);

            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static void createHeader(Sheet sheet) {
        Row first = sheet.createRow(0);
        first.createCell(0).setCellValue("货位");
        first.createCell(1).setCellValue("客户");
        first.createCell(2).setCellValue("型号");
        first.createCell(3).setCellValue("生产炉号");
        first.createCell(4).setCellValue("所在炉层");
        first.createCell(10).setCellValue("库房数量");
        first.createCell(12).setCellValue("库存重量");
        Row second = sheet.createRow(1);
        second.createCell(5).setCellValue("期初");
        second.createCell(6).setCellValue("调拨");
        second.createCell(7).setCellValue("入库");
        second.createCell(8).setCellValue("出库");
        second.createCell(9).setCellValue("退货");
        second.createCell(10).setCellValue("库房数量");
        second.createCell(11).setCellValue("单包重量");
    }

    private static void createDataRow(Sheet sheet, int rowIndex, String location, String customer,
                                      String model, String furnaceNo, String furnaceLayer,
                                      double opening, double transfer, double inbound, double outbound,
                                      double returned, double packageWeight, double stockWeight) {
        Row row = sheet.createRow(rowIndex);
        if (location != null) {
            row.createCell(0).setCellValue(location);
        }
        row.createCell(1).setCellValue(customer);
        row.createCell(2).setCellValue(model);
        row.createCell(3).setCellValue(furnaceNo);
        row.createCell(4).setCellValue(furnaceLayer);
        row.createCell(5).setCellValue(opening);
        row.createCell(6).setCellValue(transfer);
        row.createCell(7).setCellValue(inbound);
        row.createCell(8).setCellValue(outbound);
        row.createCell(9).setCellValue(returned);
        row.createCell(10).setCellFormula("F" + (rowIndex + 1) + "+G" + (rowIndex + 1)
                + "+H" + (rowIndex + 1) + "+I" + (rowIndex + 1) + "+J" + (rowIndex + 1));
        row.createCell(11).setCellValue(packageWeight);
        row.createCell(12).setCellValue(stockWeight);
    }

    private static java.math.BigDecimal decimal(String value) {
        return new java.math.BigDecimal(value);
    }

}
