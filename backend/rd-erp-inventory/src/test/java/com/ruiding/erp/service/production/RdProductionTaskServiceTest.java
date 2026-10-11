package com.ruiding.erp.service.production;

import com.ruiding.erp.controller.admin.production.vo.*;
import com.ruiding.erp.dal.dataobject.production.*;
import com.ruiding.erp.dal.mysql.production.*;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class RdProductionTaskServiceTest {
    @Mock private RdProductionTaskMapper taskMapper;
    @Mock private RdProductionOutputMapper outputMapper;
    @Mock private RdPackageUnitMapper packageMapper;
    @InjectMocks private RdProductionTaskServiceImpl service;

    @Test void createCustomerShiftTaskWithoutOrderOrInventory() {
        var req = new RdProductionTaskSaveReqVO().setProductionDate(LocalDate.of(2026,10,11))
                .setCustomerName(" customer ").setShiftCode(" night ");
        doAnswer(invocation -> { ((RdProductionTaskDO)invocation.getArgument(0)).setId(10L); return 1; })
                .when(taskMapper).insert(any(RdProductionTaskDO.class));
        assertEquals(10L, service.create(req, 2L, "manager"));
        var capture = ArgumentCaptor.forClass(RdProductionTaskDO.class);
        verify(taskMapper).insert(capture.capture());
        assertEquals("customer", capture.getValue().getCustomerName());
        assertEquals("night", capture.getValue().getShiftCode());
        assertEquals("ACTIVE", capture.getValue().getStatus());
        verifyNoInteractions(outputMapper, packageMapper);
    }

    @Test void cannotEditTaskAfterOutputIsRegistered() {
        when(taskMapper.selectByIdForUpdate(10L)).thenReturn(task());
        when(outputMapper.selectListByTaskId(10L)).thenReturn(List.of(output("DRAFT")));
        assertThrows(RuntimeException.class, () -> service.update(new RdProductionTaskSaveReqVO().setId(10L).setVersion(0)));
        verify(taskMapper, never()).updateById(any(RdProductionTaskDO.class));
    }

    @Test void closeRequiresConfirmedAndGeneratedBags() {
        when(taskMapper.selectByIdForUpdate(10L)).thenReturn(task());
        when(outputMapper.selectListByTaskId(10L)).thenReturn(List.of(output("CONFIRMED")));
        when(packageMapper.selectCountByProductionOutputId(20L)).thenReturn(1L);
        assertThrows(RuntimeException.class, () -> service.close(action()));
        verify(taskMapper, never()).updateById(any(RdProductionTaskDO.class));
    }

    @Test void closeRegistrationDoesNotRequireQualityOrInventoryCompletion() {
        var task = task();
        when(taskMapper.selectByIdForUpdate(10L)).thenReturn(task);
        when(outputMapper.selectListByTaskId(10L)).thenReturn(List.of(output("CONFIRMED")));
        when(packageMapper.selectCountByProductionOutputId(20L)).thenReturn(2L);
        service.close(action());
        assertEquals("CLOSED", task.getStatus());
        assertNotNull(task.getClosedTime());
        assertEquals(1, task.getVersion());
    }

    @Test void cancelledOutputsPermitTaskCancellationButPreserveRows() {
        var task = task();
        when(taskMapper.selectByIdForUpdate(10L)).thenReturn(task);
        when(outputMapper.selectListByTaskId(10L)).thenReturn(List.of(output("CANCELLED")));
        service.cancel(action());
        assertEquals("CANCELLED", task.getStatus());
        verify(outputMapper, never()).deleteById(anyLong());
    }

    @Test void staleVersionAndClosedTaskCannotMutate() {
        when(taskMapper.selectByIdForUpdate(10L)).thenReturn(task().setVersion(1));
        assertThrows(RuntimeException.class, () -> service.cancel(action()));
        when(taskMapper.selectByIdForUpdate(10L)).thenReturn(task().setStatus("CLOSED"));
        assertThrows(RuntimeException.class, () -> service.cancel(action()));
        verifyNoInteractions(outputMapper, packageMapper);
    }

    private static RdProductionTaskDO task() {
        return new RdProductionTaskDO().setId(10L).setTaskNo("SC-TEST").setStatus("ACTIVE").setVersion(0);
    }
    private static RdProductionOutputDO output(String status) {
        return new RdProductionOutputDO().setId(20L).setProductionTaskId(10L).setStatus(status).setOutputQty(new BigDecimal("2"));
    }
    private static RdProductionTaskActionReqVO action() { return new RdProductionTaskActionReqVO().setId(10L).setVersion(0); }
}
