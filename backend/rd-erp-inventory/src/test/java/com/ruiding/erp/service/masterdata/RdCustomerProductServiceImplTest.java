package com.ruiding.erp.service.masterdata;

import com.ruiding.erp.dal.mysql.masterdata.RdCustomerProductMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RdCustomerProductServiceImplTest {

    @Mock
    private RdCustomerProductMapper mapper;
    @InjectMocks
    private RdCustomerProductServiceImpl service;

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldTrimAndPersistCustomerProductPairIdempotently() {
        TenantContextHolder.setTenantId(1L);
        RdCustomerProductPair pair = service.recordPair("  江苏贝特瑞  ", " 026 ");

        assertEquals("江苏贝特瑞", pair.getCustomerName());
        assertEquals("026", pair.getProductModel());
        verify(mapper).upsertPair(1L, "江苏贝特瑞", "026");
    }
}
