package com.ruiding.erp.service.visual;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.ruiding.erp.dal.dataobject.production.RdProductionTaskDO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockTransactionDO;
import com.ruiding.erp.dal.mysql.stock.RdStockTransactionMapper;
import com.ruiding.erp.service.packageunit.RdPackageUnitService;
import com.ruiding.erp.service.production.RdProductionTaskService;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RdWarehouseReplayServiceTest {
    private final RdWarehouseReplayService service = new RdWarehouseReplayService();
    private final PermissionApi permissions=mock(PermissionApi.class);
    private final RdPackageUnitService packages=mock(RdPackageUnitService.class);
    private final RdProductionTaskService tasks=mock(RdProductionTaskService.class);
    private final RdStockTransactionMapper transactions=mock(RdStockTransactionMapper.class);
    @BeforeEach void setup() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser().setId(1L),null));
        ReflectionTestUtils.setField(service,"permissions",permissions);ReflectionTestUtils.setField(service,"packages",packages);
        ReflectionTestUtils.setField(service,"tasks",tasks);ReflectionTestUtils.setField(service,"transactions",transactions);
        when(permissions.hasAnyPermissions(eq(1L),anyString())).thenReturn(true);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void partialPermissionsAndDriverCannotReadTaskMembership() {
        when(permissions.hasAnyPermissions(1L,"rd:stock-transaction:query")).thenReturn(false);
        assertThrows(ServiceException.class,()->service.task(1L)); verifyNoInteractions(tasks,packages,transactions);
        when(permissions.hasAnyPermissions(1L,"rd:stock-transaction:query")).thenReturn(true);
        when(permissions.hasAnyRoles(1L,"rd_forklift_driver")).thenReturn(true);
        assertThrows(ServiceException.class,()->service.task(1L)); verifyNoInteractions(tasks,packages,transactions);
    }
    @Test void preservesLedgerLocationAndTimeInsteadOfCurrentBagPosition() {
        var task=new RdProductionTaskDO();task.setId(7L);task.setTaskNo("SC-7");task.setCustomerName("Example");when(tasks.get(7L)).thenReturn(task);
        var bag=new RdPackageUnitDO();bag.setId(1L);bag.setPackageCode("P1");bag.setProductionTaskId(7L);bag.setLocationId(999L);
        when(packages.getByProductionTaskId(7L)).thenReturn(List.of(bag));
        var e=new RdStockTransactionDO();e.setId(9L);e.setPackageId(1L);e.setLocationId(20L);e.setLocationCode("A-20");e.setQuantity(BigDecimal.ONE);e.setPackageWeight(BigDecimal.valueOf(800));e.setCreateTime(LocalDateTime.of(2026,10,15,8,10));
        when(transactions.selectList(any())).thenReturn(List.of(e));
        var result=service.task(7L);assertTrue(result.complete());assertEquals(20L,result.events().get(0).locationId());
        assertEquals("2026-10-15T08:10:00.000000+08:00",result.events().get(0).eventTime());
        assertEquals("P1",result.events().get(0).packageCode());verify(transactions,never()).insert(any(RdStockTransactionDO.class));
    }
    @Test void missingTimeIsNotReplacedWithToday() {
        var task=new RdProductionTaskDO();task.setId(7L);when(tasks.get(7L)).thenReturn(task);
        when(packages.getByProductionTaskId(7L)).thenReturn(List.of());
        var view=service.task(7L);assertTrue(view.events().isEmpty());verifyNoInteractions(transactions);
    }
}
