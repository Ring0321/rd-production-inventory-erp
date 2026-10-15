package com.ruiding.erp.service.visual;

import com.ruiding.erp.dal.mysql.visual.RdVisualDocumentMapper;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class RdVisualMapperParserTest {
    @Test
    void exactScopeQueriesRemainParseableByTheTenantInterceptor() {
        for (var method : RdVisualDocumentMapper.class.getDeclaredMethods()) {
            var select = method.getAnnotation(Select.class);
            if (select != null) {
                String sql = String.join(" ", select.value()).replaceAll("#\\{[^}]+}", "?");
                assertDoesNotThrow(() -> CCJSqlParserUtil.parse(sql), method.getName());
            }
        }
    }
}
