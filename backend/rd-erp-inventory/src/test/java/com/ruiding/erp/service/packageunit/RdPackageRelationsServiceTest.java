package com.ruiding.erp.service.packageunit;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruiding.erp.controller.admin.packageunit.RdPackageRelationsController;
import com.ruiding.erp.controller.admin.packageunit.vo.RdPackageRelationsRespVO;
import com.ruiding.erp.dal.dataobject.loading.*;
import com.ruiding.erp.dal.dataobject.masterdata.RdWarehouseLocationDO;
import com.ruiding.erp.dal.dataobject.outbound.*;
import com.ruiding.erp.dal.dataobject.packageunit.*;
import com.ruiding.erp.dal.dataobject.production.*;
import com.ruiding.erp.dal.dataobject.putaway.*;
import com.ruiding.erp.dal.dataobject.quality.*;
import com.ruiding.erp.dal.dataobject.stock.RdStockTransactionDO;
import com.ruiding.erp.dal.mysql.loading.*;
import com.ruiding.erp.dal.mysql.masterdata.RdWarehouseLocationMapper;
import com.ruiding.erp.dal.mysql.outbound.*;
import com.ruiding.erp.dal.mysql.packageunit.*;
import com.ruiding.erp.dal.mysql.production.*;
import com.ruiding.erp.dal.mysql.putaway.*;
import com.ruiding.erp.dal.mysql.quality.*;
import com.ruiding.erp.dal.mysql.stock.RdStockTransactionMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RdPackageRelationsServiceTest {
    private static final Long TENANT = 42L;
    private static final Long ROOT = 9007199254740993L;
    private final Map<Class<?>, List<TenantBaseDO>> rows = new LinkedHashMap<>();
    private final Map<Class<?>, Object> mappers = new LinkedHashMap<>();
    private final Set<String> permissions = new HashSet<>();
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private PermissionApi permissionApi;
    private RdPackageRelationsService service;
    private RdPackageUnitDO root;
    private int queryCount;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(TENANT);
        permissions.add("rd:package-unit:query");
        permissionApi = mock(PermissionApi.class);
        when(permissionApi.hasAnyPermissions(anyLong(), anyString()))
                .thenAnswer(i -> permissions.contains(i.getArgument(1, String.class)));
        service = new RdPackageRelationsService();
        ReflectionTestUtils.setField(service, "permissionApi", permissionApi);
        mapper("packageMapper", RdPackageUnitMapper.class, RdPackageUnitDO.class);
        mapper("outputMapper", RdProductionOutputMapper.class, RdProductionOutputDO.class);
        mapper("productionTaskMapper", RdProductionTaskMapper.class, RdProductionTaskDO.class);
        mapper("qualityMapper", RdQualityInspectionMapper.class, RdQualityInspectionDO.class);
        mapper("qualityMemberMapper", RdQualityInspectionMemberMapper.class, RdQualityInspectionMemberDO.class);
        mapper("putawayTaskMapper", RdPutawayTaskMapper.class, RdPutawayTaskDO.class);
        mapper("putawayLineMapper", RdPutawayTaskLineMapper.class, RdPutawayTaskLineDO.class);
        mapper("loadingTaskMapper", RdLoadingTaskMapper.class, RdLoadingTaskDO.class);
        mapper("loadingLineMapper", RdLoadingTaskLineMapper.class, RdLoadingTaskLineDO.class);
        mapper("eventMapper", RdPackageTaskEventMapper.class, RdPackageTaskEventDO.class);
        mapper("locationMapper", RdWarehouseLocationMapper.class, RdWarehouseLocationDO.class);
        mapper("outboundMapper", RdOutboundOrderMapper.class, RdOutboundOrderDO.class);
        mapper("outboundLineMapper", RdOutboundOrderLineMapper.class, RdOutboundOrderLineDO.class);
        mapper("transactionMapper", RdStockTransactionMapper.class, RdStockTransactionDO.class);
        root = add(new RdPackageUnitDO().setId(ROOT).setPackageCode("PK-ROOT").setStatus("IN_STOCK"));
    }

    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @Test void rootAndRedactionAreStablePrivateAndReadOnly() throws Exception {
        root.setCustomerName("PRIVATE_CUSTOMER").setOperatorName("PRIVATE_OPERATOR")
                .setRemark("PRIVATE_REMARK").setQrCode("PRIVATE_QR").setProductionOutputId(15L)
                .setLocationId(30L).setLocationCode("PRIVATE_LOCATION");
        var before = json.valueToTree(rows);
        var result = read();
        assertEquals("ROOT_GROUPS", result.getScope());
        assertEquals("PK-ROOT", result.getRootPackageCode());
        assertTrue(result.getRootNodeKey().contains(ROOT.toString()));
        assertEquals(ROOT, result.getNodes().get(0).getReferenceId());
        assertEquals(result.getRootNodeKey(), result.getNodes().get(0).getNodeKey());
        assertEquals(1, result.getNodes().size());
        assertTrue(result.getEdges().isEmpty());
        for (var section : result.getSections()) {
            if (!"GRAPH".equals(section.getKind())) {
                assertEquals("REDACTED", section.getState());
                assertEquals(0, section.getReturnedCount());
                assertFalse(section.getReason().contains("15"));
            }
        }
        assertFalse(json.writeValueAsString(result).contains("PRIVATE_"));
        assertEquals(before, json.valueToTree(rows));
        assertEquals(1, queryCount);
        assertEquals(json.valueToTree(result), json.valueToTree(read()));
    }

    @Test void executeOnlyAndUnauthenticatedUsersCannotBypassRootQuery() {
        permissions.clear();
        permissions.addAll(Set.of("rd:putaway-task:execute", "rd:loading-task:execute"));
        assertThrows(AccessDeniedException.class, this::read);
        assertThrows(AccessDeniedException.class, () -> service.getRelations("PK-ROOT", null));
        assertEquals(0, queryCount);
    }

    @Test void queryPermissionIsRequiredIndependentlyForEveryDomain() {
        permissions.addAll(Set.of("rd:putaway-task:execute", "rd:loading-task:execute", "rd:pickup-slip:query"));
        var result = read();
        assertEquals("REDACTED", section(result, "PUTAWAY").getState());
        assertEquals("REDACTED", section(result, "LOADING").getState());
        assertEquals(1, queryCount);
        permissions.add("rd:quality-inspection:query");
        result = read();
        assertEquals("COMPLETE", section(result, "QUALITY").getState());
        verifyNoInteractions(mappers.get(RdLoadingTaskDO.class), mappers.get(RdPutawayTaskDO.class),
                mappers.get(RdLoadingTaskLineDO.class), mappers.get(RdPutawayTaskLineDO.class),
                mappers.get(RdPackageTaskEventDO.class));
    }

    @Test void realCoverageIncludesNonsampledReviewedMembersButNotAttributeDecoys() {
        permissions.add("rd:quality-inspection:query");
        root.setProductionOutputId(15L).setCustomerName("SAME").setProductModel("M").setFurnaceNo("F");
        var peer = add(new RdPackageUnitDO().setId(2L).setPackageCode("PK-SAMPLE"));
        add(new RdPackageUnitDO().setId(3L).setPackageCode("PK-DECOY")
                .setProductionOutputId(15L).setCustomerName("SAME").setProductModel("M").setFurnaceNo("F"));
        add(new RdQualityInspectionDO().setId(20L).setInspectionNo("QI-20").setPackageId(peer.getId()).setStatus("PASSED"));
        add(new RdQualityInspectionMemberDO().setId(21L).setInspectionId(20L)
                .setPackageId(ROOT).setPackageCode("PK-ROOT").setSampled(false).setActiveKey(null));
        add(new RdQualityInspectionMemberDO().setId(22L).setInspectionId(20L)
                .setPackageId(2L).setPackageCode("PK-SAMPLE").setSampled(true).setActiveKey(null));
        add(new RdQualityInspectionDO().setId(23L).setInspectionNo("QI-DECOY").setPackageId(3L).setProductionOutputId(15L));
        var result = read();
        assertEquals(Set.of("PK-ROOT", "PK-SAMPLE"), packageCodes(result));
        assertEquals(1, nodes(result, "QUALITY_INSPECTION").size());
        var coverage = result.getEdges().stream().filter(e -> "QUALITY_COVERAGE".equals(e.getRelation())).toList();
        assertEquals(2, coverage.size());
        assertTrue(coverage.stream().anyMatch(e -> Boolean.FALSE.equals(e.getSampled()) && e.getToNodeKey().equals(result.getRootNodeKey())));
        assertTrue(coverage.stream().anyMatch(e -> Boolean.TRUE.equals(e.getSampled())));
    }

    @Test void genuineLegacyInspectionCoversOnlyItsRepresentative() {
        permissions.add("rd:quality-inspection:query");
        root.setProductionOutputId(15L);
        add(new RdPackageUnitDO().setId(2L).setPackageCode("PK-OTHER").setProductionOutputId(15L));
        add(new RdQualityInspectionDO().setId(20L).setPackageId(ROOT).setProductionOutputId(15L));
        var result = read();
        assertEquals(Set.of("PK-ROOT"), packageCodes(result));
        assertTrue(result.getEdges().stream().anyMatch(e -> "LEGACY_REPRESENTATIVE".equals(e.getRelation()) && e.getSampled() == null));
        add(new RdQualityInspectionMemberDO().setId(21L).setInspectionId(20L).setPackageId(2L).setPackageCode("PK-OTHER"));
        result = read();
        assertTrue(nodes(result, "QUALITY_INSPECTION").isEmpty());
        assertTrue(result.getEdges().isEmpty());
    }

    @Test void modernRepresentativeWithOnlyPeerMembersRemainingIsIncompleteNotInferredCoverage() {
        permissions.add("rd:quality-inspection:query");
        add(new RdQualityInspectionDO().setId(20L).setPackageId(ROOT)
                .setClientRequestId("modern-partially-missing").setStatus("PASSED"));
        add(new RdPackageUnitDO().setId(2L).setPackageCode("PK-PEER"));
        add(new RdQualityInspectionMemberDO().setId(22L).setInspectionId(20L)
                .setPackageId(2L).setPackageCode("PK-PEER").setSampled(true));
        var result = read();
        assertEquals("INCOMPLETE", section(result, "QUALITY").getState());
        assertTrue(nodes(result, "QUALITY_INSPECTION").isEmpty());
        assertTrue(result.getEdges().isEmpty());
    }

    @Test void modernInspectionWithMissingMembersIsIncompleteNotLegacy() {
        permissions.add("rd:quality-inspection:query");
        add(new RdQualityInspectionDO().setId(20L).setPackageId(ROOT).setClientRequestId("REQ-1")
                .setRegistrarId(1L).setStatus("PASSED"));
        var result = read();
        assertEquals("INCOMPLETE", section(result, "QUALITY").getState());
        assertFalse(result.getEdges().stream().anyMatch(e -> "LEGACY_REPRESENTATIVE".equals(e.getRelation())));
    }

    @Test void productionGroupUsesPersistedOutputIdentityWithoutFurnaceGuesses() {
        permissions.add("rd:production-output:query");
        root.setProductionOutputId(15L).setFurnaceNo("SAME-FURNACE");
        add(new RdProductionOutputDO().setId(15L).setStatus("CONFIRMED"));
        add(new RdPackageUnitDO().setId(2L).setPackageCode("PK-BATCH").setProductionOutputId(15L));
        add(new RdPackageUnitDO().setId(3L).setPackageCode("PK-DECOY").setProductionOutputId(99L).setFurnaceNo("SAME-FURNACE"));
        var result = read();
        assertEquals(Set.of("PK-ROOT", "PK-BATCH"), packageCodes(result));
        assertTrue(result.getEdges().stream().anyMatch(e -> "OUTPUT_MEMBER".equals(e.getRelation())));
    }

    @Test void unknownDeletedAndCrossTenantRootsFailWithoutHydratingDomains() {
        assertThrows(RuntimeException.class, () -> service.getRelations("UNKNOWN", 1L));
        root.setTenantId(99L);
        assertThrows(RuntimeException.class, this::read);
        root.setTenantId(TENANT);
        root.setDeleted(true);
        assertThrows(RuntimeException.class, this::read);
        TenantContextHolder.clear();
        int before = queryCount;
        assertThrows(RuntimeException.class, this::read);
        assertEquals(before, queryCount);
    }

    @Test void historyFindsReplacedPackageWithoutTreatingScanSourceIdAsTaskId() {
        permissions.addAll(Set.of("rd:loading-task:query", "rd:warehouse-location:query"));
        root.setStatus("OUTBOUNDED").setLocationId(30L).setLocationCode("A-30");
        add(new RdWarehouseLocationDO().setId(30L).setLocationCode("A-30"));
        add(new RdLoadingTaskDO().setId(40L).setTaskNo("LOAD-40").setStatus("ASSIGNED"));
        add(new RdLoadingTaskDO().setId(999L).setTaskNo("DECOY-SCAN-ID"));
        add(new RdPackageUnitDO().setId(2L).setPackageCode("PK-REPLACEMENT").setStatus("IN_STOCK"));
        add(new RdLoadingTaskLineDO().setId(41L).setTaskId(40L).setPackageId(2L)
                .setPackageCode("PK-REPLACEMENT").setLineStatus("PENDING"));
        add(new RdPackageTaskEventDO().setId(50L).setPackageId(ROOT).setPackageCode("PK-ROOT")
                .setStage("LOADING").setSourceType("LOADING_TASK").setSourceId(40L)
                .setSourceVersion(3).setEventCode("REPLACED_OUT").setRelatedPackageCode("PK-REPLACEMENT")
                .setLocationCode("OLD-RECORDED"));
        add(new RdPackageTaskEventDO().setId(51L).setPackageId(ROOT).setPackageCode("PK-ROOT")
                .setStage("LOADING").setSourceType("LOADING_SCAN").setSourceId(999L).setEventCode("SCAN_CONFIRMED"));
        var result = read();
        assertEquals(Set.of("PK-ROOT", "PK-REPLACEMENT"), packageCodes(result));
        assertEquals(List.of(40L), nodes(result, "LOADING_TASK").stream().map(RdPackageRelationsRespVO.Node::getReferenceId).toList());
        assertTrue(result.getEdges().stream().anyMatch(e -> "LAST_RECORDED_POSITION".equals(e.getRelation()) && e.getFromNodeKey().equals(result.getRootNodeKey())));
        assertFalse(result.getEdges().stream().anyMatch(e -> "CURRENT_POSITION".equals(e.getRelation()) && e.getFromNodeKey().equals(result.getRootNodeKey())));
        assertEquals(2, nodes(result, "TASK_EVENT").size());
        assertTrue(result.getEdges().stream().anyMatch(e -> Objects.equals(50L, e.getEvidenceId()) && Objects.equals(3, e.getSourceVersion())));
        assertTrue(nodes(result, "LOCATION_SNAPSHOT").stream().anyMatch(n -> "OLD-RECORDED".equals(n.getLocationCode())));
    }

    @Test void memberQueriesAreLimitedInSqlBeforeHydration() {
        permissions.add("rd:quality-inspection:query");
        add(new RdQualityInspectionDO().setId(20L).setPackageId(ROOT));
        add(new RdQualityInspectionMemberDO().setId(1L).setInspectionId(20L).setPackageId(ROOT).setPackageCode("PK-ROOT"));
        for (long id = 2; id <= 101; id++) {
            add(new RdPackageUnitDO().setId(id).setPackageCode("PK-" + id));
            add(new RdQualityInspectionMemberDO().setId(id).setInspectionId(20L).setPackageId(id).setPackageCode("PK-" + id));
        }
        var result = read();
        assertEquals(100, packageCodes(result).size());
        assertFalse(packageCodes(result).contains("PK-101"));
        assertEquals("LIMITED", section(result, "QUALITY").getState());
        assertFalse(section(result, "QUALITY").getReason().isBlank());
        assertTrue(queryCount < 110);
    }

    @Test void controllerContractAndRepeatableReadAreExplicit() throws Exception {
        var method = RdPackageRelationsController.class.getMethod("getRelations", String.class, javax.servlet.http.HttpServletResponse.class);
        assertEquals("@ss.hasPermission('rd:package-unit:query')", method.getAnnotation(PreAuthorize.class).value());
        var tx = RdPackageRelationsService.class.getMethod("getRelations", String.class, Long.class).getAnnotation(Transactional.class);
        assertNotNull(tx);
        assertTrue(tx.readOnly());
        assertEquals(Isolation.REPEATABLE_READ, tx.isolation());
        var controller = new RdPackageRelationsController();
        var mockService = mock(RdPackageRelationsService.class);
        ReflectionTestUtils.setField(controller, "service", mockService);
        var response = new MockHttpServletResponse();
        assertNotNull(controller.getRelations("PK-ROOT", response));
        assertEquals("no-store", response.getHeader("Cache-Control"));
    }

    @Test void productionUsesOnlyPersistedRootSourcesAndSeparatePermissions() {
        permissions.add("rd:production-output:query");
        root.setProductionOutputId(15L).setProductionTaskId(16L);
        add(new RdProductionOutputDO().setId(15L).setProductionTaskId(16L).setStatus("CONFIRMED"));
        add(new RdProductionOutputDO().setId(17L).setProductionTaskId(16L).setFurnaceNo("DECOY"));
        add(new RdProductionTaskDO().setId(16L).setTaskNo("PROD-16").setStatus("CLOSED"));
        var result = read();
        assertEquals(List.of(15L), nodes(result, "PRODUCTION_OUTPUT").stream().map(RdPackageRelationsRespVO.Node::getReferenceId).toList());
        assertTrue(nodes(result, "PRODUCTION_TASK").isEmpty());
        verifyNoInteractions(mappers.get(RdProductionTaskDO.class));
        permissions.add("rd:production-task:query");
        result = read();
        assertEquals(1, nodes(result, "PRODUCTION_TASK").size());
        assertEquals(Set.of("PK-ROOT"), packageCodes(result));
    }

    @Test void exactTaskGroupsNeverExpandPeerTasksAndPlannedIsNotPosted() {
        permissions.addAll(Set.of("rd:putaway-task:query", "rd:loading-task:query", "rd:warehouse-location:query"));
        add(new RdPackageUnitDO().setId(2L).setPackageCode("PEER"));
        add(new RdPackageUnitDO().setId(3L).setPackageCode("DECOY"));
        add(new RdWarehouseLocationDO().setId(30L).setLocationCode("PLAN"));
        add(new RdPutawayTaskDO().setId(20L).setTaskNo("PUT-20").setStatus("ASSIGNED")
                .setPlannedLocationId(30L).setPlannedLocationCode("PLAN"));
        add(new RdPutawayTaskDO().setId(99L).setTaskNo("PEER-ONLY-TASK"));
        add(new RdPutawayTaskLineDO().setId(21L).setTaskId(20L).setPackageId(ROOT).setPackageCode("PK-ROOT").setLineStatus("PENDING"));
        add(new RdPutawayTaskLineDO().setId(22L).setTaskId(20L).setPackageId(2L).setPackageCode("PEER").setLineStatus("CONFIRMED"));
        add(new RdPutawayTaskLineDO().setId(23L).setTaskId(99L).setPackageId(2L).setPackageCode("PEER"));
        add(new RdPutawayTaskLineDO().setId(24L).setTaskId(99L).setPackageId(3L).setPackageCode("DECOY"));
        add(new RdLoadingTaskDO().setId(40L).setTaskNo("LOAD-40").setStatus("CANCELLED"));
        add(new RdLoadingTaskLineDO().setId(41L).setTaskId(40L).setPackageId(ROOT).setPackageCode("PK-ROOT").setLineStatus("CANCELLED"));
        var result = read();
        assertEquals(Set.of("PK-ROOT", "PEER"), packageCodes(result));
        assertEquals(List.of(20L), nodes(result, "PUTAWAY_TASK").stream().map(RdPackageRelationsRespVO.Node::getReferenceId).toList());
        assertTrue(result.getEdges().stream().anyMatch(e -> "PLANNED_POSITION".equals(e.getRelation())));
        assertTrue(result.getEdges().stream().anyMatch(e -> "HISTORICAL_LOADING_MEMBER".equals(e.getRelation()) && "CANCELLED".equals(e.getLineStatus())));
        assertTrue(nodes(result, "OUTBOUND_ORDER").isEmpty());
        assertTrue(nodes(result, "STOCK_TRANSACTION").isEmpty());
    }

    @Test void postingUsesActualLineForeignKeysNotStockRelatedOrderId() throws Exception {
        permissions.addAll(Set.of("rd:loading-task:query", "rd:outbound-order:query", "rd:stock-transaction:query"));
        add(new RdLoadingTaskDO().setId(40L).setTaskNo("LOAD-40").setStatus("OUTBOUNDED"));
        add(new RdLoadingTaskLineDO().setId(41L).setTaskId(40L).setPackageId(ROOT).setPackageCode("PK-ROOT")
                .setLineStatus("OUTBOUNDED").setOutboundOrderId(60L).setOutboundOrderLineId(61L).setStockTransactionId(70L));
        add(new RdOutboundOrderDO().setId(60L).setOutboundNo("OUT-60").setStatus("POSTED"));
        add(new RdOutboundOrderDO().setId(888L).setOutboundNo("RELATED-ORDER-DECOY"));
        add(new RdOutboundOrderLineDO().setId(61L).setOrderId(60L).setPackageId(ROOT).setPackageCode("PK-ROOT").setStockTransactionId(70L));
        add(new RdStockTransactionDO().setId(70L).setPackageId(ROOT).setRelatedOrderId(888L)
                .setTransactionType("OUTBOUND").setIdempotencyKey("PRIVATE_KEY").setOperatorName("PRIVATE_NAME"));
        var before = json.valueToTree(rows);
        var result = read();
        assertEquals(List.of(60L), nodes(result, "OUTBOUND_ORDER").stream().map(RdPackageRelationsRespVO.Node::getReferenceId).toList());
        assertTrue(result.getEdges().stream().anyMatch(e -> "LOADING_OUTBOUND_LINE".equals(e.getRelation()) && Objects.equals(41L, e.getEvidenceId())));
        assertTrue(result.getEdges().stream().anyMatch(e -> "OUTBOUND_TRANSACTION".equals(e.getRelation()) && Objects.equals(61L, e.getEvidenceId())));
        assertEquals(List.of(70L), nodes(result, "STOCK_TRANSACTION").stream().map(RdPackageRelationsRespVO.Node::getReferenceId).toList());
        assertFalse(json.writeValueAsString(result).contains("PRIVATE_"));
        assertEquals(before, json.valueToTree(rows));
    }

    @Test void relatedRowsAndForeignKeysAreExplicitlyTenantCheckedEvenIfMapperIsWrong() {
        permissions.addAll(Set.of("rd:quality-inspection:query", "rd:production-output:query"));
        root.setProductionOutputId(15L);
        var output = add(new RdProductionOutputDO().setId(15L).setStatus("CONFIRMED"));
        output.setTenantId(99L);
        add(new RdQualityInspectionDO().setId(20L).setPackageId(ROOT));
        var member = add(new RdQualityInspectionMemberDO().setId(21L).setInspectionId(20L).setPackageId(ROOT).setPackageCode("PK-ROOT"));
        var peer = add(new RdPackageUnitDO().setId(2L).setPackageCode("HIDDEN-PEER"));
        peer.setTenantId(99L);
        var peerMember = add(new RdQualityInspectionMemberDO().setId(22L).setInspectionId(20L).setPackageId(2L).setPackageCode("HIDDEN-PEER"));
        doReturn(List.of(output)).when((RdProductionOutputMapper) mappers.get(RdProductionOutputDO.class)).selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        doReturn(List.of(member, peerMember)).when((RdQualityInspectionMemberMapper) mappers.get(RdQualityInspectionMemberDO.class)).selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        var result = read();
        assertTrue(nodes(result, "PRODUCTION_OUTPUT").isEmpty());
        assertEquals(Set.of("PK-ROOT"), packageCodes(result));
        assertEquals("INCOMPLETE", section(result, "PRODUCTION_OUTPUT").getState());
        assertEquals("INCOMPLETE", section(result, "QUALITY").getState());
    }

    @Test void nodeLimitRetainsRootAndHasNoDanglingEdges() {
        permissions.add("rd:quality-inspection:query");
        for (long group = 1; group <= 6; group++) {
            add(new RdQualityInspectionDO().setId(group).setPackageId(ROOT));
            add(new RdQualityInspectionMemberDO().setId(group * 1000).setInspectionId(group).setPackageId(ROOT).setPackageCode("PK-ROOT"));
            for (long n = 1; n <= 99; n++) {
                long id = group * 1000 + n;
                add(new RdPackageUnitDO().setId(id).setPackageCode("PK-" + id));
                add(new RdQualityInspectionMemberDO().setId(id).setInspectionId(group).setPackageId(id).setPackageCode("PK-" + id));
            }
        }
        assertGraphLimit(read(), 500, null);
    }

    @Test void edgeLimitIsExplicitAndNeverDropsRoot() {
        permissions.add("rd:quality-inspection:query");
        for (long id = 1; id <= 30; id++) add(new RdPackageUnitDO().setId(id).setPackageCode("PK-" + id));
        for (long group = 1; group <= 40; group++) {
            add(new RdQualityInspectionDO().setId(group).setPackageId(ROOT));
            add(new RdQualityInspectionMemberDO().setId(group * 1000).setInspectionId(group).setPackageId(ROOT).setPackageCode("PK-ROOT"));
            for (long n = 1; n <= 30; n++) add(new RdQualityInspectionMemberDO().setId(group * 1000 + n)
                    .setInspectionId(group).setPackageId(n).setPackageCode("PK-" + n));
        }
        assertGraphLimit(read(), null, 1000);
    }

    @Test void codeOnlyPositionIsRecordedNotVerifiedCurrentOccupancy() {
        permissions.add("rd:warehouse-location:query");
        root.setStatus("IN_STOCK").setLocationId(null).setLocationCode("CODE-ONLY");
        var result = read();
        assertFalse(result.getEdges().stream().anyMatch(e -> "CURRENT_POSITION".equals(e.getRelation())));
        assertTrue(result.getEdges().stream().anyMatch(e -> "LAST_RECORDED_POSITION".equals(e.getRelation())));
        assertEquals("INCOMPLETE", section(result, "POSITION").getState());
        verifyNoInteractions(mappers.get(RdWarehouseLocationDO.class));
    }

    @Test void inaccessibleSourceDoesNotInventACompleteProductionTaskSection() {
        permissions.add("rd:production-task:query");
        root.setProductionOutputId(15L).setProductionTaskId(null);
        var result = read();
        assertEquals("REDACTED", section(result, "PRODUCTION_OUTPUT").getState());
        assertEquals("INCOMPLETE", section(result, "PRODUCTION_TASK").getState());
        assertTrue(result.getEdges().isEmpty());
        verifyNoInteractions(mappers.get(RdProductionOutputDO.class), mappers.get(RdProductionTaskDO.class));
    }

    @Test void taskEventHistoryHasItsOwnSqlCapAndNoFreeTextProjection() throws Exception {
        permissions.add("rd:loading-task:query");
        for (long id = 1; id <= 101; id++) add(new RdPackageTaskEventDO().setId(id)
                .setPackageId(ROOT).setPackageCode("PK-ROOT").setSourceType("LOADING_SCAN")
                .setSourceId(999L).setEventCode("SCAN_CONFIRMED").setOperatorName("PRIVATE_OPERATOR")
                .setDetail("PRIVATE_DETAIL").setSourceNo("PRIVATE_SCAN_REFERENCE").setLocationCode("PRIVATE_LOCATION"));
        var result = read();
        assertEquals(100, nodes(result, "TASK_EVENT").size());
        assertFalse(nodes(result, "TASK_EVENT").stream().anyMatch(n -> Objects.equals(101L, n.getReferenceId())));
        assertTrue(nodes(result, "LOADING_TASK").isEmpty());
        assertEquals("LIMITED", section(result, "LOADING").getState());
        assertEquals("ROW_LIMIT_100", section(result, "LOADING").getReason());
        assertFalse(json.writeValueAsString(result).contains("PRIVATE_"));
        verifyNoInteractions(mappers.get(RdLoadingTaskDO.class), mappers.get(RdWarehouseLocationDO.class));
    }

    @Test void taskQueryAloneNeverDisclosesPostingOrLocationForeignKeys() throws Exception {
        permissions.add("rd:loading-task:query");
        add(new RdLoadingTaskDO().setId(40L).setTaskNo("LOAD-40").setStatus("ASSIGNED")
                .setTransportDriverPhone("PRIVATE_PHONE").setCustomerName("PRIVATE_CUSTOMER"));
        add(new RdLoadingTaskLineDO().setId(41L).setTaskId(40L).setPackageId(ROOT).setPackageCode("PK-ROOT")
                .setOutboundOrderId(600001L).setOutboundOrderLineId(600002L).setStockTransactionId(700001L)
                .setPlannedLocationId(300001L).setPlannedLocationCode("PRIVATE_LOCATION").setRemark("PRIVATE_REMARK"));
        var result = read();
        String serialized = json.writeValueAsString(result);
        assertFalse(serialized.contains("PRIVATE_"));
        for (String id : List.of("600001", "600002", "700001", "300001")) assertFalse(serialized.contains(id));
        for (String kind : List.of("OUTBOUND", "STOCK_TRANSACTION", "POSITION")) {
            assertEquals("REDACTED", section(result, kind).getState());
            assertEquals(0, section(result, kind).getReturnedCount());
        }
        verifyNoInteractions(mappers.get(RdOutboundOrderDO.class), mappers.get(RdOutboundOrderLineDO.class),
                mappers.get(RdStockTransactionDO.class), mappers.get(RdWarehouseLocationDO.class));
    }

    @Test void faultyRootMapperCannotLeakWrongTenantDeletedOrWrongCodeRows() {
        var mapper = (RdPackageUnitMapper) mappers.get(RdPackageUnitDO.class);
        doReturn(List.of(root)).when(mapper).selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        root.setTenantId(99L);
        assertThrows(RuntimeException.class, this::read);
        root.setTenantId(TENANT);
        root.setDeleted(true);
        assertThrows(RuntimeException.class, this::read);
        root.setDeleted(null);
        assertThrows(RuntimeException.class, this::read);
        root.setDeleted(false);
        root.setPackageCode("WRONG-CODE");
        assertThrows(RuntimeException.class, this::read);
        verifyNoInteractions(mappers.get(RdProductionOutputDO.class), mappers.get(RdQualityInspectionDO.class),
                mappers.get(RdLoadingTaskDO.class), mappers.get(RdStockTransactionDO.class));
    }

    @Test void brokenPostingForeignKeyDoesNotManufacturePostedStateOrAnAssociation() {
        permissions.addAll(Set.of("rd:loading-task:query", "rd:outbound-order:query"));
        add(new RdLoadingTaskDO().setId(40L).setTaskNo("LOAD-40").setStatus("PENDING_REVIEW"));
        add(new RdLoadingTaskLineDO().setId(41L).setTaskId(40L).setPackageId(ROOT).setPackageCode("PK-ROOT")
                .setLineStatus("SCANNED").setOutboundOrderId(60L).setOutboundOrderLineId(61L));
        add(new RdOutboundOrderDO().setId(60L).setOutboundNo("OUT-60").setStatus("DRAFT"));
        add(new RdOutboundOrderLineDO().setId(61L).setOrderId(60L).setPackageId(2L).setPackageCode("NOT-ROOT"));
        var result = read();
        assertTrue(nodes(result, "OUTBOUND_LINE").isEmpty());
        assertTrue(nodes(result, "OUTBOUND_ORDER").isEmpty());
        assertEquals("INCOMPLETE", section(result, "OUTBOUND").getState());
        assertFalse(result.getEdges().stream().anyMatch(e -> "LOADING_OUTBOUND_LINE".equals(e.getRelation())));
        assertFalse(result.getNodes().stream().anyMatch(n -> "POSTED".equals(n.getStatus())));
    }

    private void assertGraphLimit(RdPackageRelationsRespVO result, Integer nodeCount, Integer edgeCount) {
        if (nodeCount != null) assertEquals(nodeCount, result.getNodes().size());
        if (edgeCount != null) assertEquals(edgeCount, result.getEdges().size());
        assertTrue(result.getNodes().size() <= 500);
        assertTrue(result.getEdges().size() <= 1000);
        assertEquals("LIMITED", section(result, "GRAPH").getState());
        assertTrue(section(result, "GRAPH").getReason().contains("500"));
        assertTrue(section(result, "GRAPH").getReason().contains("1000"));
        var keys = result.getNodes().stream().map(RdPackageRelationsRespVO.Node::getNodeKey).collect(Collectors.toSet());
        assertTrue(keys.contains(result.getRootNodeKey()));
        assertEquals(keys.size(), result.getNodes().size());
        assertEquals(result.getEdges().size(), result.getEdges().stream().map(RdPackageRelationsRespVO.Edge::getEdgeKey).distinct().count());
        for (var edge : result.getEdges()) {
            assertTrue(keys.contains(edge.getFromNodeKey()));
            assertTrue(keys.contains(edge.getToNodeKey()));
        }
    }

    private RdPackageRelationsRespVO read() { return service.getRelations("PK-ROOT", 1L); }

    private RdPackageRelationsRespVO.Section section(RdPackageRelationsRespVO result, String kind) {
        return result.getSections().stream().filter(s -> kind.equals(s.getKind())).findFirst().orElseThrow();
    }

    private List<RdPackageRelationsRespVO.Node> nodes(RdPackageRelationsRespVO result, String kind) {
        return result.getNodes().stream().filter(n -> kind.equals(n.getKind())).toList();
    }

    private Set<String> packageCodes(RdPackageRelationsRespVO result) {
        return nodes(result, "PACKAGE").stream().map(RdPackageRelationsRespVO.Node::getPackageCode).collect(Collectors.toSet());
    }

    private <T extends TenantBaseDO> T add(T row) {
        row.setTenantId(TENANT);
        row.setDeleted(false);
        rows.computeIfAbsent(row.getClass(), c -> new ArrayList<>()).add(row);
        return row;
    }

    private <T extends TenantBaseDO, M extends BaseMapperX<T>> void mapper(String field, Class<M> mapperType, Class<T> type) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "relations-test"), type);
        M mapper = mock(mapperType, invocation -> {
            if ("toString".equals(invocation.getMethod().getName())) return mapperType.getSimpleName();
            assertEquals("selectList", invocation.getMethod().getName(), "Only bounded nonlocking reads are allowed");
            return select(type, invocation.getArgument(0));
        });
        mappers.put(type, mapper);
        ReflectionTestUtils.setField(service, field, mapper);
    }

    // Small in-process fixture evaluates only the conjunctions used by these bounded wrappers.
    private <T> List<T> select(Class<T> type, LambdaQueryWrapper<T> query) {
        queryCount++;
        String sql = query.getSqlSegment();
        assertTrue(sql.contains("tenant_id ="), sql);
        assertTrue(sql.contains("deleted ="), sql);
        assertFalse(sql.contains("FOR UPDATE"), sql);
        assertTrue(sql.endsWith("LIMIT 101") || sql.endsWith("LIMIT 1"), sql);
        String conditions = query.getExpression().getNormal().getSqlSegment();
        assertFalse(conditions.contains(" OR "), conditions);
        var pattern = Pattern.compile("([a-z_]+) (=|IN) (#[{]ew\\.paramNameValuePairs\\.MPGENVAL\\d+[}]|\\([^)]*\\))");
        var matcher = pattern.matcher(conditions);
        var checks = new ArrayList<java.util.function.Predicate<Object>>();
        while (matcher.find()) {
            String column = matcher.group(1);
            String property = "id".equals(column) ? "id" : TableInfoHelper.getTableInfo(type).getFieldList().stream()
                    .filter(f -> f.getColumn().equals(column)).findFirst().orElseThrow().getProperty();
            var params = Pattern.compile("MPGENVAL\\d+").matcher(matcher.group(3));
            var values = new ArrayList<Object>();
            while (params.find()) values.add(query.getParamNameValuePairs().get(params.group()));
            checks.add(row -> values.contains(SystemMetaObject.forObject(row).getValue(property)));
        }
        assertTrue(checks.size() >= 3, conditions);
        int limit = sql.endsWith("LIMIT 1") ? 1 : 101;
        return rows.getOrDefault(type, List.of()).stream().filter(row -> checks.stream().allMatch(p -> p.test(row)))
                .sorted(Comparator.comparing(row -> (Long) SystemMetaObject.forObject(row).getValue("id")))
                .limit(limit).map(type::cast).toList();
    }
}
