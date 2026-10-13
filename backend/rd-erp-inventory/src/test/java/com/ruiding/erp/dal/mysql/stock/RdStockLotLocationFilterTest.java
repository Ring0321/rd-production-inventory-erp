package com.ruiding.erp.dal.mysql.stock;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ruiding.erp.controller.admin.stock.vo.RdStockLotPageReqVO;
import com.ruiding.erp.dal.dataobject.stock.RdStockLotDO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import javax.validation.Validation;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RdStockLotLocationFilterTest {
    @BeforeAll static void table() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RdStockLotDO.class);
    }
    @Test void locationIdIsExactAndLegacyCodeQueryRemainsAvailable() {
        RdStockLotMapper mapper = mock(RdStockLotMapper.class, CALLS_REAL_METHODS);
        var request = new RdStockLotPageReqVO();
        request.setLocationId(42L);
        request.setLocationCode("A-1");
        doReturn(null).when(mapper).selectPage(eq(request), any(LambdaQueryWrapper.class));
        mapper.selectPage(request);
        ArgumentCaptor<LambdaQueryWrapper> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectPage(eq(request), query.capture());
        assertTrue(query.getValue().getSqlSegment().contains("location_id ="));
        assertTrue(query.getValue().getSqlSegment().contains("location_code LIKE"));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(42L));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue("%A-1%"));
    }
    @Test void absentIdentityDoesNotForceAZeroOrInventedLocation() {
        RdStockLotMapper mapper = mock(RdStockLotMapper.class, CALLS_REAL_METHODS);
        var request = new RdStockLotPageReqVO();
        doReturn(null).when(mapper).selectPage(eq(request), any(LambdaQueryWrapper.class));
        mapper.selectPage(request);
        ArgumentCaptor<LambdaQueryWrapper> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectPage(eq(request), query.capture());
        assertFalse(query.getValue().getSqlSegment().contains("location_id ="));
    }
    @Test void invalidLocationIdentityIsRejectedByTheRequestContract() {
        var request = new RdStockLotPageReqVO();
        request.setLocationId(-1L);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(request).stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("locationId")));
        }
    }
}
