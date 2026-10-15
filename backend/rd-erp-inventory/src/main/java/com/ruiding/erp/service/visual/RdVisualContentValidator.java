package com.ruiding.erp.service.visual;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruiding.erp.dal.dataobject.masterdata.RdWarehouseLocationDO;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
public class RdVisualContentValidator {
    private static final Set<String> LABEL_KEYS = Set.of("brand", "packageCode", "qrCode", "customerName",
            "productModel", "furnaceNo", "furnaceLayer", "packageWeight", "productionDate");

    public JsonNode validate(String kind, String scopeId, JsonNode doc, Map<Long, RdWarehouseLocationDO> locations) {
        require(doc != null && doc.isObject() && doc.toString().length() <= 262144, "配置内容无效或过大");
        require(doc.path("schemaVersion").isIntegralNumber() && doc.path("schemaVersion").canConvertToInt() && doc.path("schemaVersion").intValue() == 1, "配置版本不支持");
        if ("LAYOUT".equals(kind)) layout(scopeId, doc, locations);
        else if ("LABEL".equals(kind)) label(scopeId, doc);
        else throw new IllegalArgumentException("配置类型无效");
        return doc.deepCopy();
    }

    private void layout(String warehouse, JsonNode doc, Map<Long, RdWarehouseLocationDO> locations) {
        keys(doc, Set.of("schemaVersion", "warehouseCode", "width", "height", "elements"));
        require(warehouse != null && warehouse.equals(text(doc, "warehouseCode", 64)), "仓库编码不匹配");
        double width = number(doc, "width", 64, 4096), height = number(doc, "height", 64, 4096);
        var elements = doc.path("elements");
        require(elements.isArray() && elements.size() <= 1000, "布局元素最多1000个");
        var ids = new HashSet<String>(); var bound = new HashSet<Long>();
        for (var e : elements) {
            keys(e, Set.of("id", "kind", "locationId", "x", "y", "width", "height", "label"));
            String id = text(e, "id", 64), type = text(e, "kind", 20);
            require(id.matches("[A-Za-z0-9_-]+") && ids.add(id), "布局元素编号重复或无效");
            require(Set.of("LOCATION", "AISLE", "ZONE").contains(type), "布局元素类型不支持");
            rectangle(e, width, height, 1); text(e, "label", 80);
            if ("LOCATION".equals(type)) {
                var n = e.path("locationId");
                require(n.isIntegralNumber() && n.canConvertToLong() && n.longValue() > 0 && bound.add(n.longValue()), "库位绑定缺失或重复");
                var row = locations.get(n.longValue());
                require(row != null && warehouse.equals(row.getWarehouseCode()) && Boolean.TRUE.equals(row.getEnabled()), "库位不存在、禁用或不属于此仓库");
            } else require(!e.has("locationId") || e.get("locationId").isNull(), "区域或通道不能绑定库位");
        }
    }

    private void label(String scope, JsonNode doc) {
        require("GLOBAL".equals(scope), "标签模板范围无效");
        keys(doc, Set.of("schemaVersion", "name", "widthMm", "heightMm", "fields"));
        text(doc, "name", 80);
        double width = number(doc, "widthMm", 50, 210), height = number(doc, "heightMm", 30, 297);
        var fields = doc.path("fields");
        require(fields.isArray() && fields.size() >= 2 && fields.size() <= LABEL_KEYS.size(), "模板字段数量无效");
        var names = new HashSet<String>();
        for (var field : fields) {
            keys(field, Set.of("key", "x", "y", "width", "height", "fontSize"));
            String key = text(field, "key", 40);
            require(LABEL_KEYS.contains(key) && names.add(key), "模板字段重复或不支持");
            rectangle(field, width, height, 1);
            number(field, "fontSize", 6, 36);
            if ("qrCode".equals(key)) {
                double w = number(field, "width", 20, 80), h = number(field, "height", 20, 80);
                require(Math.abs(w - h) < 0.01, "二维码区域应为至少20毫米的正方形");
            }
        }
        require(names.contains("packageCode") && names.contains("qrCode"), "模板必须保留包号和二维码");
    }

    private static void rectangle(JsonNode node, double width, double height, double min) {
        double x = number(node, "x", 0, width), y = number(node, "y", 0, height);
        double w = number(node, "width", min, width), h = number(node, "height", min, height);
        require(x + w <= width + 0.00001 && y + h <= height + 0.00001, "元素超出画布边界");
    }
    private static double number(JsonNode node, String key, double min, double max) {
        var value = node.path(key); double n = value.asDouble(Double.NaN);
        require(value.isNumber() && Double.isFinite(n) && n >= min && n <= max, "数值字段无效：" + key);
        return n;
    }
    private static String text(JsonNode node, String key, int max) {
        var value = node.path(key); String s = value.asText("");
        require(value.isTextual() && !s.isBlank() && s.length() <= max
                && !s.matches("(?s).*[<>\\p{Cntrl}].*") && !s.matches("(?is).*(https?://|javascript:|data:).*"), "文本字段无效：" + key);
        return s;
    }
    private static void keys(JsonNode node, Set<String> allowed) {
        require(node.isObject(), "配置结构必须为对象");
        node.fieldNames().forEachRemaining(key -> require(allowed.contains(key), "配置含不支持的属性"));
    }
    private static void require(boolean valid, String message) { if (!valid) throw new IllegalArgumentException(message); }
}
