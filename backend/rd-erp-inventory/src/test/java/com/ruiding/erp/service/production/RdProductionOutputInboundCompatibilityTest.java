package com.ruiding.erp.service.production;

import com.ruiding.erp.dal.dataobject.production.RdProductionOutputDO;
import com.ruiding.erp.dal.mysql.production.RdProductionOutputMapper;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.enums.RdProductionOutputStatusEnum;
import com.ruiding.erp.service.masterdata.RdCustomerProductService;
import com.ruiding.erp.service.stock.RdStockLotService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RdProductionOutputInboundCompatibilityTest {

    @Mock private RdProductionOutputMapper productionOutputMapper;
    @Mock private RdStockLotService stockLotService;
    @Mock private RdCustomerProductService customerProductService;
    @Mock private RdPackageUnitMapper packageUnitMapper;
    @InjectMocks private RdProductionOutputServiceImpl service;

    @Test
    void shouldRejectLegacyDirectInboundWhenProductionHasNoPackages() {
        when(productionOutputMapper.selectById(1L)).thenReturn(new RdProductionOutputDO()
                .setId(1L).setLocationCode("3-22").setOutputQty(BigDecimal.ONE)
                .setStatus(RdProductionOutputStatusEnum.CONFIRMED.getStatus()));

        assertThrows(RuntimeException.class, () -> service.inboundProductionOutput(1L));

        verify(stockLotService, never()).createProductionInbound(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectCancellingProductionAfterPackagesWereGenerated() {
        when(productionOutputMapper.selectById(1L)).thenReturn(new RdProductionOutputDO()
                .setId(1L).setStatus(RdProductionOutputStatusEnum.CONFIRMED.getStatus()));
        when(packageUnitMapper.selectCountByProductionOutputId(1L)).thenReturn(1L);

        assertThrows(RuntimeException.class, () -> service.cancelProductionOutput(1L));
    }
}
