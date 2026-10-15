package com.ruiding.erp.service.visual;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruiding.erp.dal.dataobject.masterdata.RdWarehouseLocationDO;
import com.ruiding.erp.dal.dataobject.visual.RdVisualDocumentDO;
import com.ruiding.erp.dal.mysql.masterdata.RdWarehouseLocationMapper;
import com.ruiding.erp.dal.mysql.visual.RdVisualDocumentEventMapper;
import com.ruiding.erp.dal.mysql.visual.RdVisualDocumentMapper;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RdVisualDocumentServiceTest {
    private final RdVisualDocumentService service = new RdVisualDocumentService();
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final RdVisualDocumentMapper documents = mock(RdVisualDocumentMapper.class);
    private final RdVisualDocumentEventMapper events = mock(RdVisualDocumentEventMapper.class);
    private final RdWarehouseLocationMapper locations = mock(RdWarehouseLocationMapper.class);
    private final ObjectMapper json = new ObjectMapper();
    @BeforeEach void setup() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser().setId(1L), null));
        ReflectionTestUtils.setField(service,"permissions",permissions); ReflectionTestUtils.setField(service,"documents",documents);
        ReflectionTestUtils.setField(service,"events",events); ReflectionTestUtils.setField(service,"locations",locations);
        ReflectionTestUtils.setField(service,"json",json); ReflectionTestUtils.setField(service,"validator",new RdVisualContentValidator());
        when(permissions.hasAnyPermissions(eq(1L),anyString())).thenReturn(true);
        var location = new RdWarehouseLocationDO(); location.setId(1L); location.setEnabled(true); location.setWarehouseCode("A");
        when(locations.selectList(any())).thenReturn(List.of(location));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void driversAreRejectedBeforeAnyConfigurationReadOrWrite() {
        when(permissions.hasAnyRoles(1L,"rd_forklift_driver")).thenReturn(true);
        assertThrows(ServiceException.class,()->service.get("LABEL","GLOBAL"));
        assertThrows(ServiceException.class,()->service.publish("LAYOUT","A",0));
        verifyNoInteractions(documents,events,locations);
    }
    @Test void ordinaryPrintAuthorityDoesNotPermitTemplateAdministration() {
        assertThrows(ServiceException.class,()->service.publish("LABEL","GLOBAL",0));
        verifyNoInteractions(documents,events);
    }
    @Test void staleVersionCannotOverwriteOrCreateRevision() throws Exception {
        var row = new RdVisualDocumentDO(); row.setVersion(4); row.setId(1L);
        when(documents.lock("LAYOUT","A")).thenReturn(row);
        var content=json.readTree("{\"schemaVersion\":1,\"warehouseCode\":\"A\",\"width\":800,\"height\":600,\"elements\":[]}");
        var error=assertThrows(ServiceException.class,()->service.save("LAYOUT","A",3,content));
        assertEquals(1080900001,error.getCode());
        verify(documents,never()).updateById(any(RdVisualDocumentDO.class)); verify(documents,never()).insert(any(RdVisualDocumentDO.class)); verifyNoInteractions(events);
    }
    @Test void emptyDocumentIsVersionZeroAndPublishingRequiresSavedDraft() {
        assertEquals(0,service.get("LABEL","GLOBAL").version());
        when(permissions.hasAnyRoles(1L,"super_admin")).thenReturn(true);
        assertThrows(IllegalArgumentException.class,()->service.publish("LABEL","GLOBAL",0));
        verifyNoInteractions(events);
    }
    @Test void disabledLocationAfterSavePreventsPublication() throws Exception {
        var row=new RdVisualDocumentDO(); row.setId(1L); row.setVersion(1);
        row.setDraftContent("{\"schemaVersion\":1,\"warehouseCode\":\"A\",\"width\":800,\"height\":600,\"elements\":[{\"id\":\"l1\",\"kind\":\"LOCATION\",\"locationId\":2,\"x\":0,\"y\":0,\"width\":80,\"height\":80,\"label\":\"A2\"}]}");
        when(documents.lock("LAYOUT","A")).thenReturn(row);
        assertThrows(IllegalArgumentException.class,()->service.publish("LAYOUT","A",1));
        verify(documents,never()).updateById(any(RdVisualDocumentDO.class)); verifyNoInteractions(events);
    }
}
