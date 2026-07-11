package com.ruiding.erp.service.excelimport;

import lombok.Getter;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Streams the current-stock section of the historical inventory workbook.
 * Only columns A:M are retained, so legacy formatting outside the business area
 * cannot expand the in-memory row model.
 */
@Component
public class RdInventoryExcelParser {

    public static final String DEFAULT_SHEET_NAME = "货位统计明细表";

    public static final String ROW_STATUS_VALID = "VALID";
    public static final String ROW_STATUS_ERROR = "ERROR";
    public static final String ROW_STATUS_SKIPPED = "SKIPPED";

    private static final int HEADER_ROW_COUNT = 2;
    private static final int LAST_BUSINESS_COLUMN_INDEX = 12;
    private static final int MAX_BUSINESS_ROWS = 100_000;
    private static final BigDecimal WEIGHT_TOLERANCE = new BigDecimal("0.01");

    public ParseResult parse(InputStream inputStream, String sheetName) throws IOException {
        String targetSheetName = isBlank(sheetName) ? DEFAULT_SHEET_NAME : sheetName.trim();
        List<ParsedRow> rows = new ArrayList<>();
        boolean sheetFound = false;
        try (OPCPackage opcPackage = OPCPackage.open(inputStream)) {
            XSSFReader reader = new XSSFReader(opcPackage, true);
            StylesTable styles = reader.getStylesTable();
            SharedStrings sharedStrings = reader.getSharedStringsTable();
            XSSFReader.SheetIterator iterator = reader.getSheetIterator();
            while (iterator.hasNext()) {
                try (InputStream sheetStream = iterator.next()) {
                    if (!targetSheetName.equals(iterator.getSheetName())) {
                        continue;
                    }
                    sheetFound = true;
                    XMLReader xmlReader = XMLHelper.newXMLReader();
                    xmlReader.setContentHandler(new XSSFSheetXMLHandler(
                            styles, sharedStrings, new InventorySheetHandler(rows),
                            new DataFormatter(Locale.CHINA), false));
                    xmlReader.parse(new InputSource(sheetStream));
                    break;
                }
            }
        } catch (Exception exception) {
            throw new IOException("读取库存 Excel 失败: " + exception.getMessage(), exception);
        }
        if (!sheetFound) {
            throw new IOException("Excel 中不存在工作表: " + targetSheetName);
        }
        return new ParseResult(rows);
    }

    private static ParsedRow parseRow(int rowNo, Map<Integer, String> cells) {
        ParsedRow row = new ParsedRow(rowNo,
                value(cells, 0), value(cells, 1), value(cells, 2), value(cells, 3), value(cells, 4));
        row.sourceOpeningQty = parseOptionalDecimal(value(cells, 5));
        row.warehouseQty = parseRequiredDecimal("warehouseQty", "库房数量", value(cells, 10), row.errors);

        if (row.warehouseQty != null && row.warehouseQty.signum() == 0) {
            row.status = ROW_STATUS_SKIPPED;
            row.errorMessage = "库房数量为 0，未纳入当前库存迁移";
            return row;
        }

        validateRequiredText("locationCode", "货位", row.locationCode, 64, row.errors);
        validateRequiredText("customerName", "客户", row.customerName, 128, row.errors);
        validateRequiredText("productModel", "型号", row.productModel, 128, row.errors);
        validateRequiredText("furnaceNo", "生产炉号", row.furnaceNo, 64, row.errors);
        validateRequiredText("furnaceLayer", "所在炉层", row.furnaceLayer, 32, row.errors);

        if (row.warehouseQty != null && row.warehouseQty.signum() < 0) {
            row.errors.add(new FieldError("warehouseQty", row.warehouseQty.toPlainString(), "库房数量不能小于 0"));
        }
        row.packageWeight = parseRequiredDecimal("packageWeight", "单包重量", value(cells, 11), row.errors);
        if (row.packageWeight != null && row.packageWeight.signum() <= 0) {
            row.errors.add(new FieldError("packageWeight", row.packageWeight.toPlainString(), "单包重量必须大于 0"));
        }
        row.stockWeight = parseOptionalDecimalWithError(
                "stockWeight", "库存重量", value(cells, 12), row.errors);

        if (row.warehouseQty != null && row.packageWeight != null
                && row.warehouseQty.signum() > 0 && row.packageWeight.signum() > 0) {
            BigDecimal calculatedWeight = row.warehouseQty.multiply(row.packageWeight);
            if (row.stockWeight == null) {
                row.stockWeight = calculatedWeight;
            } else if (row.stockWeight.subtract(calculatedWeight).abs().compareTo(WEIGHT_TOLERANCE) > 0) {
                row.errors.add(new FieldError("stockWeight", row.stockWeight.toPlainString(),
                        "库存重量应等于库房数量 × 单包重量，计算值为 " + calculatedWeight.stripTrailingZeros().toPlainString()));
            }
        }

        if (row.errors.isEmpty()) {
            row.status = ROW_STATUS_VALID;
        } else {
            row.status = ROW_STATUS_ERROR;
            row.errorMessage = row.errors.stream().map(FieldError::getErrorMessage)
                    .collect(Collectors.joining("；"));
        }
        return row;
    }

    private static void validateRequiredText(String fieldName, String label, String rawValue,
                                             int maxLength, List<FieldError> errors) {
        if (isBlank(rawValue)) {
            errors.add(new FieldError(fieldName, rawValue, label + "不能为空"));
        } else if (rawValue.length() > maxLength) {
            errors.add(new FieldError(fieldName, rawValue, label + "不能超过 " + maxLength + " 个字符"));
        }
    }

    private static BigDecimal parseRequiredDecimal(String fieldName, String label, String rawValue,
                                                   List<FieldError> errors) {
        if (isBlank(rawValue)) {
            errors.add(new FieldError(fieldName, rawValue, label + "不能为空"));
            return null;
        }
        return parseOptionalDecimalWithError(fieldName, label, rawValue, errors);
    }

    private static BigDecimal parseOptionalDecimalWithError(String fieldName, String label, String rawValue,
                                                            List<FieldError> errors) {
        if (isBlank(rawValue)) {
            return null;
        }
        try {
            return decimal(rawValue);
        } catch (NumberFormatException exception) {
            errors.add(new FieldError(fieldName, rawValue, label + "不是有效数字"));
            return null;
        }
    }

    private static BigDecimal parseOptionalDecimal(String rawValue) {
        if (isBlank(rawValue)) {
            return null;
        }
        try {
            return decimal(rawValue);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value.replace(",", "").trim());
    }

    private static String value(Map<Integer, String> cells, int index) {
        String value = cells.get(index);
        return value == null ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean isBusinessRow(Map<Integer, String> cells) {
        boolean hasCoreDimension = !isBlank(value(cells, 1))
                || !isBlank(value(cells, 2)) || !isBlank(value(cells, 3));
        boolean hasLocationAndQuantity = !isBlank(value(cells, 0)) && !isBlank(value(cells, 10));
        return hasCoreDimension || hasLocationAndQuantity;
    }

    private static final class InventorySheetHandler implements XSSFSheetXMLHandler.SheetContentsHandler {

        private final List<ParsedRow> rows;
        private final Map<Integer, String> currentCells = new HashMap<>();

        private InventorySheetHandler(List<ParsedRow> rows) {
            this.rows = rows;
        }

        @Override
        public void startRow(int rowNum) {
            currentCells.clear();
        }

        @Override
        public void endRow(int rowNum) {
            if (rowNum < HEADER_ROW_COUNT || !isBusinessRow(currentCells)) {
                return;
            }
            if (rows.size() >= MAX_BUSINESS_ROWS) {
                throw new IllegalStateException("单次导入不能超过 " + MAX_BUSINESS_ROWS + " 条业务数据");
            }
            rows.add(parseRow(rowNum + 1, currentCells));
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            if (cellReference == null) {
                return;
            }
            int columnIndex = new CellReference(cellReference).getCol();
            if (columnIndex <= LAST_BUSINESS_COLUMN_INDEX) {
                currentCells.put(columnIndex, formattedValue);
            }
        }

    }

    @Getter
    public static final class ParseResult {

        private final List<ParsedRow> rows;
        private final int totalRows;
        private final int validRows;
        private final int failedRows;
        private final int skippedRows;

        private ParseResult(List<ParsedRow> rows) {
            this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
            this.totalRows = rows.size();
            this.validRows = countStatus(rows, ROW_STATUS_VALID);
            this.failedRows = countStatus(rows, ROW_STATUS_ERROR);
            this.skippedRows = countStatus(rows, ROW_STATUS_SKIPPED);
        }

        private static int countStatus(List<ParsedRow> rows, String status) {
            return (int) rows.stream().filter(row -> status.equals(row.status)).count();
        }

    }

    @Getter
    public static final class ParsedRow {

        private final int rowNo;
        private final String locationCode;
        private final String customerName;
        private final String productModel;
        private final String furnaceNo;
        private final String furnaceLayer;
        private BigDecimal sourceOpeningQty;
        private BigDecimal warehouseQty;
        private BigDecimal packageWeight;
        private BigDecimal stockWeight;
        private String status;
        private String errorMessage;
        private final List<FieldError> errors = new ArrayList<>();

        private ParsedRow(int rowNo, String locationCode, String customerName, String productModel,
                          String furnaceNo, String furnaceLayer) {
            this.rowNo = rowNo;
            this.locationCode = locationCode;
            this.customerName = customerName;
            this.productModel = productModel;
            this.furnaceNo = furnaceNo;
            this.furnaceLayer = furnaceLayer;
        }

    }

    @Getter
    public static final class FieldError {

        private final String fieldName;
        private final String rawValue;
        private final String errorMessage;

        private FieldError(String fieldName, String rawValue, String errorMessage) {
            this.fieldName = fieldName;
            this.rawValue = rawValue;
            this.errorMessage = errorMessage;
        }

    }

}
