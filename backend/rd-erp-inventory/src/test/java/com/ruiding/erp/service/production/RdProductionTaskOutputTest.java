package com.ruiding.erp.service.production;

import com.ruiding.erp.controller.admin.production.vo.RdProductionOutputSaveReqVO;
import com.ruiding.erp.dal.dataobject.production.*;
import com.ruiding.erp.dal.mysql.production.*;
import com.ruiding.erp.service.masterdata.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import java.time.LocalDate;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class RdProductionTaskOutputTest {
    @Mock private RdProductionTaskMapper productionTaskMapper;
    @Mock private RdProductionOutputMapper productionOutputMapper;
    @Mock private RdCustomerProductService customerProductService;
    @Mock private com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper packageUnitMapper;
    @InjectMocks private RdProductionOutputServiceImpl service;

    @Test void bindAndInheritTaskIdentityWithStableReplay() {
        var task = task();
        when(productionTaskMapper.selectByIdForUpdate(10L)).thenReturn(task);
        when(customerProductService.recordPair("customer", "model")).thenReturn(new RdCustomerProductPair("customer", "model"));
        doAnswer(invocation -> { ((RdProductionOutputDO)invocation.getArgument(0)).setId(20L); return 1; })
                .when(productionOutputMapper).insert(any(RdProductionOutputDO.class));
        var req = req();
        assertEquals(20L, service.createProductionOutput(req));
        var capture = ArgumentCaptor.forClass(RdProductionOutputDO.class);
        verify(productionOutputMapper).insert(capture.capture());
        var output = capture.getValue();
        assertEquals(10L, output.getProductionTaskId());
        assertEquals("SC-TEST", output.getProductionTaskNo());
        assertEquals("night", output.getShiftCode());
        assertEquals("DRAFT", output.getStatus());
        assertEquals(new BigDecimal("2000"), output.getOutputWeight());
        when(productionOutputMapper.selectByClientRequestId("request-1")).thenReturn(output);
        task.setStatus("CLOSED");
        assertEquals(20L, service.createProductionOutput(req));
        verify(productionOutputMapper, times(1)).insert(any(RdProductionOutputDO.class));
        assertThrows(RuntimeException.class, () -> service.createProductionOutput(req().setPackageWeight(new BigDecimal("600"))));
        output.setStatus("CANCELLED");
        task.setCustomerName("changed").setShiftCode("day").setProductionDate(LocalDate.of(2026,10,12));
        assertEquals(20L, service.createProductionOutput(req));
        assertThrows(RuntimeException.class, () -> service.createProductionOutput(req().setLocationCode("NEW-WAREHOUSE")));
    }

    @Test void draftUpdateCannotReleaseItsOriginalCreationRequest() {
        var task = task();
        when(productionTaskMapper.selectByIdForUpdate(10L)).thenReturn(task);
        when(customerProductService.recordPair("customer", "model")).thenReturn(new RdCustomerProductPair("customer", "model"));
        doAnswer(invocation -> { ((RdProductionOutputDO)invocation.getArgument(0)).setId(20L); return 1; })
                .when(productionOutputMapper).insert(any(RdProductionOutputDO.class));
        service.createProductionOutput(req());
        var capture = ArgumentCaptor.forClass(RdProductionOutputDO.class);
        verify(productionOutputMapper).insert(capture.capture());
        var output = capture.getValue().setVersion(1);
        when(productionOutputMapper.selectById(20L)).thenReturn(output);
        when(productionOutputMapper.selectByIdForUpdate(20L)).thenReturn(output);
        assertThrows(RuntimeException.class, () -> service.updateProductionOutput(req().setId(20L).setVersion(1).setClientRequestId("request-2")));
        service.updateProductionOutput(req().setId(20L).setVersion(1).setClientRequestId(null));
        var updated = ArgumentCaptor.forClass(RdProductionOutputDO.class);
        verify(productionOutputMapper).updateById(updated.capture());
        assertEquals("request-1", updated.getValue().getClientRequestId());
        assertEquals(output.getRequestDigest(), updated.getValue().getRequestDigest());
        when(productionOutputMapper.selectByClientRequestId("request-1")).thenReturn(output);
        assertEquals(20L, service.createProductionOutput(req()));
        verify(productionOutputMapper, times(1)).insert(any(RdProductionOutputDO.class));
    }

    @Test void rejectWrongCustomerDateShiftAndWarehouseFields() {
        when(productionTaskMapper.selectByIdForUpdate(10L)).thenReturn(task());
        assertThrows(RuntimeException.class, () -> service.createProductionOutput(req().setCustomerName("other")));
        assertThrows(RuntimeException.class, () -> service.createProductionOutput(req().setProductionDate(LocalDate.of(2026,10,12))));
        assertThrows(RuntimeException.class, () -> service.createProductionOutput(req().setShiftCode("day")));
        assertThrows(RuntimeException.class, () -> service.createProductionOutput(req().setLocationCode("A-01")));
        assertThrows(RuntimeException.class, () -> service.createProductionOutput(req().setInboundQty(BigDecimal.ONE)));
        verifyNoInteractions(customerProductService);
        verify(productionOutputMapper, never()).insert(any(RdProductionOutputDO.class));
    }

    @Test void rejectMissingRequestFractionalBagsAndNonDraft() {
        when(productionTaskMapper.selectByIdForUpdate(10L)).thenReturn(task());
        assertThrows(RuntimeException.class, () -> service.createProductionOutput(req().setClientRequestId(null)));
        assertThrows(RuntimeException.class, () -> service.createProductionOutput(req().setOutputQty(new BigDecimal("1.5"))));
        assertThrows(RuntimeException.class, () -> service.createProductionOutput(req().setStatus("CONFIRMED")));
        verify(productionOutputMapper, never()).insert(any(RdProductionOutputDO.class));
    }

    @Test void closedTaskCannotCreateAnotherOutput() {
        when(productionTaskMapper.selectByIdForUpdate(10L)).thenReturn(task().setStatus("CLOSED"));
        assertThrows(RuntimeException.class, () -> service.createProductionOutput(req()));
        verify(productionOutputMapper, never()).insert(any(RdProductionOutputDO.class));
    }

    private static RdProductionTaskDO task() {
        return new RdProductionTaskDO().setId(10L).setTaskNo("SC-TEST").setProductionDate(LocalDate.of(2026,10,11))
                .setCustomerName("customer").setShiftCode("night").setStatus("ACTIVE").setVersion(0);
    }
    private static RdProductionOutputSaveReqVO req() {
        return new RdProductionOutputSaveReqVO().setProductionTaskId(10L).setClientRequestId("request-1")
                .setProductionDate(LocalDate.of(2026,10,11)).setCustomerName("customer").setProductModel("model")
                .setFurnaceNo("F-1").setFurnaceLayer("lower").setOutputQty(new BigDecimal("2"))
                .setPackageWeight(new BigDecimal("1000"));
    }
}
