package com.ruiding.erp.service.masterdata;

import com.ruiding.erp.dal.dataobject.masterdata.RdWarehouseLocationDO;
import com.ruiding.erp.dal.mysql.masterdata.RdWarehouseLocationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RdWarehouseLocationFreezeGuardTest {

    @Mock
    private RdWarehouseLocationMapper locationMapper;

    @InjectMocks
    private RdWarehouseLocationFreezeGuardImpl guard;

    @Test
    void shouldRejectFrozenLocation() {
        when(locationMapper.selectByIdForUpdate(10L)).thenReturn(new RdWarehouseLocationDO()
                .setId(10L).setEnabled(true).setFrozen(true));

        assertThrows(RuntimeException.class, () -> guard.requireMovable(10L));
    }

    @Test
    void shouldAllowEnabledUnfrozenLocation() {
        when(locationMapper.selectByIdForUpdate(10L)).thenReturn(new RdWarehouseLocationDO()
                .setId(10L).setEnabled(true).setFrozen(false));

        assertDoesNotThrow(() -> guard.requireMovable(10L));
    }

    @Test
    void shouldRejectMissingOrDisabledLocation() {
        when(locationMapper.selectByIdForUpdate(10L)).thenReturn(null);
        assertThrows(RuntimeException.class, () -> guard.requireMovable(10L));

        when(locationMapper.selectByIdForUpdate(11L)).thenReturn(new RdWarehouseLocationDO()
                .setId(11L).setEnabled(false).setFrozen(false));
        assertThrows(RuntimeException.class, () -> guard.requireMovable(11L));
    }
}
