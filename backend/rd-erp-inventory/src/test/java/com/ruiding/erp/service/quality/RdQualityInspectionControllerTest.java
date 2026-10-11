package com.ruiding.erp.service.quality;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.ruiding.erp.controller.admin.quality.RdQualityInspectionController;
import com.ruiding.erp.controller.admin.quality.vo.*;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionItemDO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RdQualityInspectionControllerTest {
    private RdQualityInspectionService service;
    private MockMvc mvc;

    @BeforeEach void setup() {
        RdQualityInspectionBatchTest.login(100L);
        service = mock(RdQualityInspectionService.class);
        var controller = new RdQualityInspectionController();
        ReflectionTestUtils.setField(controller, "service", service);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }
    @AfterEach void clearLogin() { SecurityContextHolder.clearContext(); }

    @Test void shouldPreserveScopeContractAndPaginationQueryNames() throws Exception {
        var representative = new RdQualityInspectionScopeRespVO.Package().setEligible(true);
        representative.setId(1L);
        var result = new RdQualityInspectionScopeRespVO().setRepresentative(representative)
                .setProductionOutputId(101L).setPackages(new PageResult<>(List.of(
                        new RdQualityInspectionScopeRespVO.Package().setEligible(false).setActiveInspectionId(500L).setActiveInspectionNo("QC-1")), 3L));
        when(service.scope(any())).thenReturn(result);
        mvc.perform(get("/rd/quality-inspection/scope").param("packageCode", "/pkg/PKG-1").param("pageNo", "2").param("pageSize", "20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.representative.id").value(1))
                .andExpect(jsonPath("$.data.representative.eligible").value(true))
                .andExpect(jsonPath("$.data.productionOutputId").value(101))
                .andExpect(jsonPath("$.data.packages.total").value(3))
                .andExpect(jsonPath("$.data.packages.list[0].eligible").value(false))
                .andExpect(jsonPath("$.data.packages.list[0].activeInspectionId").value(500))
                .andExpect(jsonPath("$.data.packages.list[0].activeInspectionNo").value("QC-1"));
        var request = ArgumentCaptor.forClass(RdQualityInspectionScopeReqVO.class);
        verify(service).scope(request.capture());
        assertEquals(2, request.getValue().getPageNo());
        assertEquals(20, request.getValue().getPageSize());
        assertEquals("/pkg/PKG-1", request.getValue().getPackageCode());
    }

    @Test void shouldCreateBatchWithExactRequestNamesAndReturnLongId() throws Exception {
        when(service.createBatch(any())).thenReturn(500L);
        mvc.perform(post("/rd/quality-inspection/create-batch").contentType(MediaType.APPLICATION_JSON)
                .content("{\"packageId\":1,\"packageIds\":[1,3],\"samplePackageIds\":[1],\"items\":[{\"samplePackageId\":1,"
                        + "\"itemCode\":\"D\",\"itemName\":\"Density\",\"measuredValue\":1,\"qualified\":true,\"id\":999}],"
                        + "\"clientRequestId\":\"request-1\",\"remark\":\"actual samples\",\"registrarId\":999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(500));
        var request = ArgumentCaptor.forClass(RdQualityInspectionBatchCreateReqVO.class);
        verify(service).createBatch(request.capture());
        assertEquals(List.of(1L, 3L), request.getValue().getPackageIds());
        assertEquals(List.of(1L), request.getValue().getSamplePackageIds());
        assertEquals(1L, request.getValue().getItems().get(0).getSamplePackageId());
        assertEquals("request-1", request.getValue().getClientRequestId());
    }

    @Test void shouldRejectMissingSampleValueAndRequestIdAtHttpBoundary() throws Exception {
        mvc.perform(post("/rd/quality-inspection/create-batch").contentType(MediaType.APPLICATION_JSON)
                .content("{\"packageId\":1,\"packageIds\":[1],\"samplePackageIds\":[1],\"items\":[{\"samplePackageId\":1,\"itemCode\":\"D\",\"itemName\":\"Density\"}]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void shouldExtendGetWithRealMembersAndKeepItemSampleIdNullable() throws Exception {
        var detail = new RdQualityInspectionDetailRespVO();
        detail.setId(500L).setProductionOutputId(101L).setClientRequestId("request-1").setRegistrarId(100L)
                .setRegistrarName("server-100").setRequestDigest("private digest");
        detail.setMembers(List.of(new RdQualityInspectionMemberRespVO().setPackageId(3L).setPackageCode("PKG-3")
                .setPackageWeight(java.math.BigDecimal.TEN).setSampled(false))).setCoverageCount(1).setSampleCount(0);
        detail.setItems(List.of(new RdQualityInspectionItemDO().setItemCode("D").setSamplePackageId(3L)));
        when(service.getDetail(500L)).thenReturn(detail);
        mvc.perform(get("/rd/quality-inspection/get").param("id", "500"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.members[0].sampled").value(false))
                .andExpect(jsonPath("$.data.coverageCount").value(1)).andExpect(jsonPath("$.data.sampleCount").value(0))
                .andExpect(jsonPath("$.data.items[0].samplePackageId").value(3))
                .andExpect(jsonPath("$.data.registrarId").value(100)).andExpect(jsonPath("$.data.requestDigest").doesNotExist());
    }

    @Test void shouldKeepSubmitApproveRejectRoutesBoundToLoginIdentity() throws Exception {
        mvc.perform(post("/rd/quality-inspection/submit").param("id", "500")).andExpect(status().isOk());
        mvc.perform(post("/rd/quality-inspection/approve").param("id", "500")).andExpect(status().isOk());
        mvc.perform(post("/rd/quality-inspection/reject").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":500,\"reason\":\"out of range\"}")).andExpect(status().isOk());
        verify(service).submit(500L, 100L, "server-100");
        verify(service).approve(500L, 100L, "server-100");
        verify(service).reject(500L, 100L, "server-100", "out of range");
    }

    @Test void shouldKeepOldCreateAndServerFilteredPageRoutes() throws Exception {
        when(service.create(eq(1L), anyList(), isNull())).thenReturn(500L);
        when(service.page(any())).thenReturn(new PageResult<>(List.of(), 7L));
        mvc.perform(post("/rd/quality-inspection/create").contentType(MediaType.APPLICATION_JSON)
                .content("{\"packageId\":1,\"items\":[{\"itemCode\":\"D\",\"itemName\":\"Density\",\"measuredValue\":1}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(500));
        mvc.perform(get("/rd/quality-inspection/page").param("status", "SUBMITTED").param("inspectionNo", "QC-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(7));
        var req = ArgumentCaptor.forClass(RdQualityInspectionPageReqVO.class);
        verify(service).page(req.capture());
        assertEquals("SUBMITTED", req.getValue().getStatus());
        assertEquals("QC-1", req.getValue().getInspectionNo());
    }

    @Test void shouldReuseExistingPermissionsForEveryEndpoint() {
        Map<String, String> permissions = Map.of("scope", "query", "get", "query", "page", "query",
                "create", "create", "createBatch", "create", "submit", "submit", "approve", "approve", "reject", "approve");
        for (var method : RdQualityInspectionController.class.getDeclaredMethods()) {
            if (permissions.containsKey(method.getName())) {
                assertEquals("@ss.hasPermission('rd:quality-inspection:" + permissions.get(method.getName()) + "')",
                        method.getAnnotation(PreAuthorize.class).value());
            }
        }
    }
}
