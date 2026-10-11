package com.ruiding.erp.service.production;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.ruiding.erp.controller.admin.production.vo.RdProductionLayerOptionRespVO;
import com.ruiding.erp.dal.mysql.production.RdProductionChoiceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RdProductionChoiceServiceTest {
    @Mock private RdProductionChoiceMapper mapper;
    @InjectMocks private RdProductionChoiceServiceImpl service;
    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    @Test void usesAuthenticatedTenantAndBoundedNormalizedQuery() {
        TenantContextHolder.setTenantId(7L);
        when(mapper.selectFurnaces(7L,"customer","model","1-13",100)).thenReturn(List.of("1-13#28"));
        assertEquals(List.of("1-13#28"),service.furnaces(" customer "," model "," 1-13 ",10000));
        verify(mapper).selectFurnaces(7L,"customer","model","1-13",100);
    }

    @Test void dictionaryPriorityDoesNotRewriteOriginalHistoricalLayer() {
        TenantContextHolder.setTenantId(1L);
        var dict = new RdProductionLayerOptionRespVO().setValue("下层").setLabel("下层 (X)").setSource("DICTIONARY");
        var duplicate = new RdProductionLayerOptionRespVO().setValue("下层").setLabel("下层").setSource("HISTORY");
        var original = new RdProductionLayerOptionRespVO().setValue("下层（X）").setLabel("下层（X）").setSource("HISTORY");
        when(mapper.selectEnabledLayers(1L,null,50)).thenReturn(List.of(dict));
        when(mapper.selectHistoryLayers(1L,null,null,null,50)).thenReturn(List.of(duplicate,original));
        var result=service.layers(null,null,null,null);
        assertEquals(2,result.size()); assertSame(dict,result.get(0)); assertSame(original,result.get(1));
    }

    @Test void neverUsesAnImplicitDefaultTenant() {
        assertThrows(RuntimeException.class,()->service.furnaces(null,null,null,null));
        verifyNoInteractions(mapper);
    }
}
