package com.ruiding.erp.service.production;

import com.ruiding.erp.dal.dataobject.ordersplit.RdOrderSplitOutputBindDO;
import com.ruiding.erp.dal.dataobject.production.RdProductionOutputDO;
import com.ruiding.erp.dal.mysql.ordersplit.RdOrderSplitOutputBindMapper;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.dal.mysql.production.RdProductionOutputMapper;
import com.ruiding.erp.enums.RdProductionOutputStatusEnum;
import com.ruiding.erp.service.masterdata.RdCustomerProductPair;
import com.ruiding.erp.service.masterdata.RdCustomerProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RdProductionOutputConfirmTest {
    @InjectMocks private RdProductionOutputServiceImpl service;
    @Mock private RdProductionOutputMapper productionOutputMapper;
    @Mock private RdOrderSplitOutputBindMapper bindMapper;
    @Mock private RdPackageUnitMapper packageUnitMapper;
    @Mock private RdCustomerProductService customerProductService;

    @Test
    void shouldConfirmDraftWithMatchingVersion() {
        when(productionOutputMapper.selectByIdForUpdate(1L)).thenReturn(output("DRAFT", 2));
        service.confirmProductionOutput(1L, 2);
        verify(productionOutputMapper).updateById(org.mockito.ArgumentMatchers.<RdProductionOutputDO>argThat(value -> "CONFIRMED".equals(value.getStatus())
                && Integer.valueOf(3).equals(value.getVersion())));
    }

    @Test
    void shouldRejectConfirmWhenNotDraft() {
        when(productionOutputMapper.selectByIdForUpdate(1L)).thenReturn(output("CONFIRMED", 2));
        assertThrows(RuntimeException.class, () -> service.confirmProductionOutput(1L, 2));
    }

    @Test
    void shouldRejectConfirmWithStaleVersion() {
        when(productionOutputMapper.selectByIdForUpdate(1L)).thenReturn(output("DRAFT", 3));
        assertThrows(RuntimeException.class, () -> service.confirmProductionOutput(1L, 2));
    }

    @Test
    void shouldRejectUpdateWhenOutputIsActivelyBound() {
        when(productionOutputMapper.selectByIdForUpdate(1L)).thenReturn(output(RdProductionOutputStatusEnum.CONFIRMED.getStatus(), 1));
        when(bindMapper.selectActiveByProductionOutputId(1L)).thenReturn(new RdOrderSplitOutputBindDO().setId(9L));
        assertThrows(RuntimeException.class, () -> service.updateProductionOutput(
                new com.ruiding.erp.controller.admin.production.vo.RdProductionOutputSaveReqVO().setId(1L).setVersion(1)));
        verify(productionOutputMapper).selectByIdForUpdate(1L);
        verify(productionOutputMapper, never()).updateById(org.mockito.ArgumentMatchers.<RdProductionOutputDO>any());
    }

    @Test
    void shouldRejectUpdateWithStaleVersionBeforeWriting() {
        when(productionOutputMapper.selectByIdForUpdate(1L)).thenReturn(output(RdProductionOutputStatusEnum.DRAFT.getStatus(), 2));
        assertThrows(RuntimeException.class, () -> service.updateProductionOutput(
                new com.ruiding.erp.controller.admin.production.vo.RdProductionOutputSaveReqVO().setId(1L).setVersion(1)));
        verify(productionOutputMapper, never()).updateById(org.mockito.ArgumentMatchers.<RdProductionOutputDO>any());
    }

    @Test
    void shouldUpdateLockedOutputAndIncrementVersion() {
        when(productionOutputMapper.selectByIdForUpdate(1L)).thenReturn(output(RdProductionOutputStatusEnum.DRAFT.getStatus(), 2));
        when(packageUnitMapper.selectCountByProductionOutputId(1L)).thenReturn(0L);
        when(customerProductService.recordPair("Customer", "Model"))
                .thenReturn(new RdCustomerProductPair("Customer", "Model"));
        service.updateProductionOutput(new com.ruiding.erp.controller.admin.production.vo.RdProductionOutputSaveReqVO()
                .setId(1L).setVersion(2).setCustomerName("Customer").setProductModel("Model")
                .setOutputQty(java.math.BigDecimal.ONE).setPackageWeight(java.math.BigDecimal.TEN));
        verify(productionOutputMapper).updateById(org.mockito.ArgumentMatchers.<RdProductionOutputDO>argThat(
                value -> Integer.valueOf(3).equals(value.getVersion())));
    }

    @Test
    void shouldCancelLockedOutputWithMatchingVersion() {
        when(productionOutputMapper.selectByIdForUpdate(1L)).thenReturn(output(RdProductionOutputStatusEnum.DRAFT.getStatus(), 2));
        when(packageUnitMapper.selectCountByProductionOutputId(1L)).thenReturn(0L);
        service.cancelProductionOutput(1L, 2);
        verify(productionOutputMapper).updateById(org.mockito.ArgumentMatchers.<RdProductionOutputDO>argThat(
                value -> "CANCELLED".equals(value.getStatus())
                        && Integer.valueOf(3).equals(value.getVersion())));
    }

    @Test
    void shouldRejectCancelWithStaleVersionBeforeBindingLookup() {
        when(productionOutputMapper.selectByIdForUpdate(1L)).thenReturn(output(RdProductionOutputStatusEnum.DRAFT.getStatus(), 2));
        assertThrows(RuntimeException.class, () -> service.cancelProductionOutput(1L, 1));
        verify(bindMapper, never()).selectActiveByProductionOutputId(1L);
    }

    private static RdProductionOutputDO output(String status, int version) {
        return new RdProductionOutputDO().setId(1L).setStatus(status).setVersion(version);
    }
}
