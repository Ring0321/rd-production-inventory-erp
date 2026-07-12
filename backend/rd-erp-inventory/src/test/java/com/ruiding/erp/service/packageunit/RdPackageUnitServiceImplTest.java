package com.ruiding.erp.service.packageunit;

import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.mysql.packageunit.RdLabelPrintLogMapper;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.dal.mysql.production.RdProductionOutputMapper;
import com.ruiding.erp.dal.dataobject.production.RdProductionOutputDO;
import com.ruiding.erp.enums.RdPackageStatusEnum;
import com.ruiding.erp.enums.RdProductionOutputStatusEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import java.math.BigDecimal;
import java.time.LocalDate;

@ExtendWith(MockitoExtension.class)
class RdPackageUnitServiceImplTest {

    @Mock private RdPackageUnitMapper packageMapper;
    @Mock private RdProductionOutputMapper productionMapper;
    @Mock private RdLabelPrintLogMapper printLogMapper;
    @InjectMocks private RdPackageUnitServiceImpl service;

    @Test
    void shouldRequireReasonWhenReprintingLabel() {
        when(packageMapper.selectByIdForUpdate(1L)).thenReturn(new RdPackageUnitDO()
                .setId(1L).setStatus(RdPackageStatusEnum.LABELED.getStatus()));

        assertThrows(RuntimeException.class, () -> service.markPrinted(1L, 2L, "operator", null));
    }

    @Test
    void shouldLockProductionRowBeforeAllocatingPackageCodes() {
        RdProductionOutputDO production = new RdProductionOutputDO().setId(1L)
                .setProductionDate(LocalDate.of(2026, 7, 12)).setCustomerName("customer")
                .setProductModel("026").setFurnaceNo("1-01").setFurnaceLayer("X")
                .setOutputQty(BigDecimal.ONE).setPackageWeight(new BigDecimal("1000"))
                .setStatus(RdProductionOutputStatusEnum.CONFIRMED.getStatus());
        when(productionMapper.selectByIdForUpdate(1L)).thenReturn(production);
        when(packageMapper.selectCountByProductionOutputId(1L)).thenReturn(0L);
        when(packageMapper.selectCurrentDailySequenceForUpdate(1L, production.getProductionDate())).thenReturn(1);

        service.generate(1L, 1);

        verify(productionMapper).selectByIdForUpdate(1L);
        verify(packageMapper).incrementDailySequence(1L, production.getProductionDate(), 1);
    }
}
