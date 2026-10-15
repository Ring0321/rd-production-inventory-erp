package com.ruiding.erp.service.visual;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruiding.erp.dal.dataobject.masterdata.RdWarehouseLocationDO;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RdVisualContentValidatorTest {
    private final ObjectMapper json = new ObjectMapper();
    private final RdVisualContentValidator validator = new RdVisualContentValidator();
    private JsonNode layout(String extra) throws Exception {
        return json.readTree("{\"schemaVersion\":1,\"warehouseCode\":\"A\",\"width\":1000,\"height\":600,\"elements\":["
                + "{\"id\":\"loc-1\",\"kind\":\"LOCATION\",\"locationId\":1,\"x\":20,\"y\":40,\"width\":100,\"height\":80,\"label\":\"A-01\"}" + extra + "]}");
    }
    private Map<Long, RdWarehouseLocationDO> locations(String warehouse, boolean enabled) {
        var row = new RdWarehouseLocationDO(); row.setId(1L); row.setWarehouseCode(warehouse); row.setEnabled(enabled);
        return Map.of(1L, row);
    }
    @Test void acceptsOnlyActualSameWarehouseLocationWithoutWritingIt() throws Exception {
        var original = layout("");
        assertEquals(original, validator.validate("LAYOUT", "A", original, locations("A", true)));
        assertEquals("A", locations("A", true).get(1L).getWarehouseCode());
    }
    @Test void rejectsWrongWarehouseDisabledDuplicateAndOutOfBounds() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> validator.validate("LAYOUT", "A", layout(""), locations("B", true)));
        assertThrows(IllegalArgumentException.class, () -> validator.validate("LAYOUT", "A", layout(""), locations("A", false)));
        String repeated = ",{\"id\":\"loc-2\",\"kind\":\"LOCATION\",\"locationId\":1,\"x\":200,\"y\":40,\"width\":100,\"height\":80,\"label\":\"A-01\"}";
        assertThrows(IllegalArgumentException.class, () -> validator.validate("LAYOUT", "A", layout(repeated), locations("A", true)));
        var outside = layout(""); ((com.fasterxml.jackson.databind.node.ObjectNode) outside.get("elements").get(0)).put("x", 999);
        assertThrows(IllegalArgumentException.class, () -> validator.validate("LAYOUT", "A", outside, locations("A", true)));
    }
    @Test void rejectsArbitraryPropertiesHtmlAndExternalResources() throws Exception {
        var doc = (com.fasterxml.jackson.databind.node.ObjectNode) layout(""); doc.put("script", "alert(1)");
        assertThrows(IllegalArgumentException.class, () -> validator.validate("LAYOUT", "A", doc, locations("A", true)));
        var html = layout(""); ((com.fasterxml.jackson.databind.node.ObjectNode) html.get("elements").get(0)).put("label", "<img src=x>");
        assertThrows(IllegalArgumentException.class, () -> validator.validate("LAYOUT", "A", html, locations("A", true)));
    }
    private JsonNode label() throws Exception {
        return json.readTree("{\"schemaVersion\":1,\"name\":\"Standard\",\"widthMm\":150,\"heightMm\":100,\"fields\":["
                + "{\"key\":\"packageCode\",\"x\":5,\"y\":70,\"width\":100,\"height\":12,\"fontSize\":14},"
                + "{\"key\":\"qrCode\",\"x\":105,\"y\":10,\"width\":35,\"height\":35,\"fontSize\":12}]}");
    }
    @Test void acceptsLogicalTemplateButNotPackageValuesRawPdfOrMissingIdentity() throws Exception {
        assertEquals(label(), validator.validate("LABEL", "GLOBAL", label(), Map.of()));
        var doc = (com.fasterxml.jackson.databind.node.ObjectNode) label(); doc.put("basePdf", "https://invalid.test/doc.pdf");
        assertThrows(IllegalArgumentException.class, () -> validator.validate("LABEL", "GLOBAL", doc, Map.of()));
        var missing = label(); ((com.fasterxml.jackson.databind.node.ObjectNode) missing.get("fields").get(0)).put("key", "customerName");
        assertThrows(IllegalArgumentException.class, () -> validator.validate("LABEL", "GLOBAL", missing, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> validator.validate("LABEL", "other", label(), Map.of()));
    }
    @Test void rejectsFractionalSchemaVersionsAndStringDimensions() throws Exception {
        var doc = (com.fasterxml.jackson.databind.node.ObjectNode) label(); doc.put("schemaVersion", 1.1);
        assertThrows(IllegalArgumentException.class, () -> validator.validate("LABEL", "GLOBAL", doc, Map.of()));
        var text = (com.fasterxml.jackson.databind.node.ObjectNode) label(); text.put("widthMm", "150");
        assertThrows(IllegalArgumentException.class, () -> validator.validate("LABEL", "GLOBAL", text, Map.of()));
        var huge = (com.fasterxml.jackson.databind.node.ObjectNode) label(); huge.put("schemaVersion", 4294967297L);
        assertThrows(IllegalArgumentException.class, () -> validator.validate("LABEL", "GLOBAL", huge, Map.of()));
    }
}
