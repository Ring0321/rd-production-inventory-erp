package com.ruiding.erp.service.quality;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import com.ruiding.erp.controller.admin.quality.vo.*;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.production.RdProductionOutputDO;
import com.ruiding.erp.dal.dataobject.production.RdProductionTaskDO;
import com.ruiding.erp.dal.dataobject.quality.*;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.dal.mysql.production.RdProductionOutputMapper;
import com.ruiding.erp.dal.mysql.production.RdProductionTaskMapper;
import com.ruiding.erp.dal.mysql.quality.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.LongStream;

import static com.ruiding.erp.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RdQualityInspectionBatchTest {
    @Mock private RdQualityInspectionMapper inspectionMapper;
    @Mock private RdQualityInspectionItemMapper itemMapper;
    @Mock private RdQualityInspectionMemberMapper memberMapper;
    @Mock private RdQualityInspectionEventMapper eventMapper;
    @Mock private RdPackageUnitMapper packageMapper;
    @Mock private RdProductionOutputMapper outputMapper;
    @Mock private RdProductionTaskMapper productionTaskMapper;
    @InjectMocks private RdQualityInspectionServiceImpl service;

    @BeforeAll static void initializeQueryMetadata() {
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(
                new com.baomidou.mybatisplus.core.MybatisConfiguration(), "d3-test");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, RdPackageUnitDO.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, RdQualityInspectionDO.class);
    }

    @BeforeEach void loginRegistrar() {
        login(100L);
        lenient().when(eventMapper.insert(any(RdQualityInspectionEventDO.class))).thenReturn(1);
    }
    @AfterEach void clearLogin() { SecurityContextHolder.clearContext(); }

    @Test void shouldClaimOnlyPartialOutputWithRealSamplesAndSortedProductionLocks() {
        stubSourceAndPackages("LABELED");
        when(productionTaskMapper.selectByIdForUpdate(55L)).thenReturn(new RdProductionTaskDO().setId(55L).setStatus("CLOSED"));
        stubNewWrites();

        assertEquals(500L, service.createBatch(request()));

        InOrder locks = inOrder(productionTaskMapper, outputMapper, packageMapper);
        locks.verify(outputMapper).selectById(101L);
        locks.verify(productionTaskMapper).selectByIdForUpdate(55L);
        locks.verify(outputMapper).selectByIdForUpdate(101L);
        locks.verify(packageMapper).selectByIdForUpdate(1L);
        locks.verify(packageMapper).selectByIdForUpdate(3L);
        ArgumentCaptor<RdQualityInspectionMemberDO> members = ArgumentCaptor.forClass(RdQualityInspectionMemberDO.class);
        verify(memberMapper, times(2)).insert(members.capture());
        assertEquals(List.of(1L, 3L), members.getAllValues().stream().map(RdQualityInspectionMemberDO::getPackageId).toList());
        assertEquals(List.of(true, false), members.getAllValues().stream().map(RdQualityInspectionMemberDO::getSampled).toList());
        assertTrue(members.getAllValues().stream().allMatch(member -> member.getActiveKey().equals(member.getPackageId())
                && new BigDecimal("1000").compareTo(member.getPackageWeight()) == 0 && member.getPackageCode() != null));
        verify(packageMapper, never()).selectByIdForUpdate(2L);
        verify(packageMapper, never()).updateById(any(RdPackageUnitDO.class));
        assertEquals(100L, insertedHeader().getRegistrarId());
        assertEquals("server-100", insertedHeader().getRegistrarName());
        assertEquals(101L, insertedHeader().getProductionOutputId());
    }

    @Test void shouldBlockBatchOverlapIncludingLegacyDraftsBeforeAnyInsert() {
        stubSourceAndPackages("LABELED");
        when(inspectionMapper.selectActiveByPackageId(1L, null)).thenReturn(null);
        when(inspectionMapper.selectActiveByPackageId(3L, null)).thenReturn(new RdQualityInspectionDO().setId(900L).setStatus("DRAFT"));
        assertError(QUALITY_INSPECTION_PACKAGE_CLAIMED, () -> service.createBatch(request()));
        verify(inspectionMapper, never()).insert(any(RdQualityInspectionDO.class));
        verifyNoInteractions(memberMapper, itemMapper);
    }

    @ParameterizedTest @ValueSource(strings = {"DRAFT", "SUBMITTED"})
    void shouldBlockOldSingleCreateAgainstActiveSingleOrBatchClaims(String status) {
        RdPackageUnitDO unit = unit(1L, "LABELED").setProductionOutputId(null);
        when(packageMapper.selectById(1L)).thenReturn(unit);
        when(packageMapper.selectByIdForUpdate(1L)).thenReturn(unit);
        when(inspectionMapper.selectActiveByPackageId(1L, null)).thenReturn(new RdQualityInspectionDO().setId(900L).setStatus(status));
        assertError(QUALITY_INSPECTION_PACKAGE_CLAIMED, () -> service.create(1L, List.of(item(1L)), null));
        verify(inspectionMapper, never()).insert(any(RdQualityInspectionDO.class));
    }

    @ParameterizedTest @ValueSource(strings = {"output", "customer", "model", "furnace", "layer"})
    void shouldRejectMixedSourceAndCoreFields(String field) {
        stubSourceAndPackages("LABELED");
        RdPackageUnitDO other = unit(3L, "LABELED");
        switch (field) {
            case "output" -> other.setProductionOutputId(102L);
            case "customer" -> other.setCustomerName("other");
            case "model" -> other.setProductModel("other");
            case "furnace" -> other.setFurnaceNo("other");
            case "layer" -> other.setFurnaceLayer("other");
        }
        when(packageMapper.selectByIdForUpdate(3L)).thenReturn(other);
        assertError(QUALITY_INSPECTION_SOURCE_MISMATCH, () -> service.createBatch(request()));
        verify(inspectionMapper, never()).insert(any(RdQualityInspectionDO.class));
        verifyNoInteractions(memberMapper, itemMapper);
    }

    @ParameterizedTest @ValueSource(strings = {"CREATED", "QC_PENDING", "QC_PASSED", "IN_STOCK", "VOIDED"})
    void shouldRejectIneligibleMembersWithoutPartialDraft(String status) {
        stubSourceAndPackages("LABELED");
        when(packageMapper.selectByIdForUpdate(3L)).thenReturn(unit(3L, status));
        assertError(PACKAGE_STATUS_INVALID, () -> service.createBatch(request()));
        verify(inspectionMapper, never()).insert(any(RdQualityInspectionDO.class));
        verifyNoInteractions(memberMapper, itemMapper);
    }

    @Test void shouldRejectMissingPackageBeforeWriting() {
        stubSource();
        when(packageMapper.selectById(1L)).thenReturn(unit(1L, "LABELED"));
        when(packageMapper.selectByIdForUpdate(1L)).thenReturn(unit(1L, "LABELED"));
        assertError(PACKAGE_NOT_EXISTS, () -> service.createBatch(request()));
        verify(inspectionMapper, never()).insert(any(RdQualityInspectionDO.class));
    }

    @Test void shouldAllowNoSourceOnlyAsOneRealPackage() {
        RdPackageUnitDO unit = unit(1L, "LABELED").setProductionOutputId(null);
        when(packageMapper.selectById(1L)).thenReturn(unit);
        when(packageMapper.selectByIdForUpdate(1L)).thenReturn(unit);
        stubNewWrites();
        service.createBatch(request().setPackageIds(List.of(1L)));
        assertNull(insertedHeader().getProductionOutputId());
        verifyNoInteractions(outputMapper, productionTaskMapper);
    }

    @Test void shouldRejectMultipleNoSourcePackages() {
        when(packageMapper.selectById(1L)).thenReturn(unit(1L, "LABELED").setProductionOutputId(null));
        when(packageMapper.selectByIdForUpdate(1L)).thenReturn(unit(1L, "LABELED").setProductionOutputId(null));
        when(packageMapper.selectByIdForUpdate(3L)).thenReturn(unit(3L, "LABELED").setProductionOutputId(null));
        assertError(QUALITY_INSPECTION_SOURCE_MISMATCH, () -> service.createBatch(request()));
        verify(inspectionMapper, never()).insert(any(RdQualityInspectionDO.class));
    }

    @Test void shouldRejectInvalidSelectionsBeforeReadingDatabase() {
        List<RdQualityInspectionBatchCreateReqVO> requests = List.of(
                request().setPackageIds(List.of(1L, 1L)), request().setPackageIds(List.of()),
                request().setPackageIds(LongStream.rangeClosed(1, 201).boxed().toList()),
                request().setPackageIds(List.of(3L)), request().setSamplePackageIds(List.of(2L)),
                request().setSamplePackageIds(List.of(1L, 1L)), request().setSamplePackageIds(List.of()),
                request().setPackageIds(Arrays.asList(1L, null)), request().setClientRequestId(" "));
        for (var req : requests) assertError(QUALITY_INSPECTION_SELECTION_INVALID, () -> service.createBatch(req));
        verifyNoInteractions(packageMapper, outputMapper, inspectionMapper, memberMapper, itemMapper);
    }

    @Test void shouldRequireActualItemsForEverySampleAndRejectBadBounds() {
        List<RdQualityInspectionBatchCreateReqVO> requests = List.of(
                request().setSamplePackageIds(List.of(1L, 3L)), request().setItems(List.of()),
                request().setItems(List.of(requestItem().setSamplePackageId(3L))),
                request().setItems(List.of(requestItem().setMeasuredValue(null))),
                request().setItems(List.of(requestItem().setStandardMin(BigDecimal.TEN).setStandardMax(BigDecimal.ONE))),
                request().setItems(List.of(requestItem().setItemCode(" "))),
                request().setItems(List.of(requestItem().setMeasuredValue(new BigDecimal("0.0000001")))));
        for (var req : requests) assertError(QUALITY_INSPECTION_ITEMS_INVALID, () -> service.createBatch(req));
        verifyNoInteractions(packageMapper, inspectionMapper, memberMapper, itemMapper);
    }

    @Test void shouldIgnoreIncomingIdsAuditStatusAndQualifiedInBatchJson() {
        String json = "{\"packageId\":1,\"packageIds\":[3,1],\"samplePackageIds\":[1],\"clientRequestId\":\"request-1\","
                + "\"id\":999,\"status\":\"PASSED\",\"registrarId\":999,\"tenantId\":999,\"items\":[{"
                + "\"samplePackageId\":1,\"itemCode\":\"D\",\"itemName\":\"Density\",\"standardMax\":0.5,\"measuredValue\":1,"
                + "\"id\":888,\"inspectionId\":888,\"tenantId\":999,\"creator\":\"forged\",\"qualified\":true}]}";
        var req = JsonUtils.parseObject(json, RdQualityInspectionBatchCreateReqVO.class);
        stubSourceAndPackages("LABELED");
        stubNewWrites();
        service.createBatch(req);
        assertEquals(500L, insertedHeader().getId());
        assertEquals("DRAFT", insertedHeader().getStatus());
        assertEquals(100L, insertedHeader().getRegistrarId());
        ArgumentCaptor<RdQualityInspectionItemDO> item = ArgumentCaptor.forClass(RdQualityInspectionItemDO.class);
        verify(itemMapper).insert(item.capture());
        assertFalse(item.getValue().getQualified());
        assertNull(item.getValue().getId());
        assertNull(item.getValue().getTenantId());
        assertNull(item.getValue().getCreator());
        assertEquals(500L, item.getValue().getInspectionId());
        assertFalse(JsonUtils.toJsonString(insertedHeader()).contains("requestDigest"));
    }

    @Test void shouldSanitizeOldItemDoWithoutMutatingCallerData() {
        var unit = unit(1L, "QC_REJECTED").setProductionOutputId(null);
        when(packageMapper.selectById(1L)).thenReturn(unit);
        when(packageMapper.selectByIdForUpdate(1L)).thenReturn(unit);
        stubNewWrites();
        var malicious = item(1L).setId(999L).setInspectionId(999L).setQualified(true).setStandardMax(BigDecimal.ZERO);
        malicious.setTenantId(999L).setCreator("forged").setDeleted(true);
        malicious.setCreateTime(LocalDateTime.of(2099, 1, 1, 0, 0));
        service.create(1L, List.of(malicious), null);
        ArgumentCaptor<RdQualityInspectionItemDO> stored = ArgumentCaptor.forClass(RdQualityInspectionItemDO.class);
        verify(itemMapper).insert(stored.capture());
        assertNotSame(malicious, stored.getValue());
        assertNull(stored.getValue().getId());
        assertNull(stored.getValue().getTenantId());
        assertNull(stored.getValue().getCreator());
        assertNull(stored.getValue().getCreateTime());
        assertFalse(stored.getValue().getQualified());
        assertEquals(999L, malicious.getId());
    }

    @ParameterizedTest @ValueSource(strings = {"DRAFT", "SUBMITTED", "PASSED", "REJECTED"})
    void shouldReplayExactCreateAfterStateChangesWithoutNewWrites(String state) {
        stubSourceAndPackages("LABELED");
        stubNewWrites();
        var req = request();
        service.createBatch(req);
        var saved = insertedHeader().setStatus(state);
        when(inspectionMapper.selectByClientRequestId(req.getClientRequestId())).thenReturn(saved);
        clearInvocations(packageMapper, outputMapper, memberMapper, itemMapper, inspectionMapper);

        assertEquals(500L, service.createBatch(req));

        verifyNoInteractions(packageMapper, outputMapper, memberMapper, itemMapper);
        verify(inspectionMapper, never()).insert(any(RdQualityInspectionDO.class));
    }

    @Test void shouldRejectCreateReplayFromOtherActorOrChangedContent() {
        stubSourceAndPackages("LABELED");
        stubNewWrites();
        service.createBatch(request());
        var saved = insertedHeader().setStatus("PASSED");
        when(inspectionMapper.selectByClientRequestId("request-1")).thenReturn(saved);
        clearInvocations(packageMapper, memberMapper, itemMapper);
        assertError(QUALITY_INSPECTION_REQUEST_CONFLICT, () -> service.createBatch(request().setRemark("changed")));
        assertError(QUALITY_INSPECTION_REQUEST_CONFLICT, () -> service.createBatch(request().setPackageIds(List.of(1L, 3L))));
        login(200L);
        assertError(QUALITY_INSPECTION_REQUEST_CONFLICT, () -> service.createBatch(request()));
        verifyNoInteractions(packageMapper, memberMapper, itemMapper);
    }

    @Test void shouldReReadConcurrentCreateReplayAfterLocksBeforeChangedPackageState() {
        stubSourceAndPackages("LABELED");
        stubNewWrites();
        service.createBatch(request());
        var saved = insertedHeader().setStatus("PASSED");
        when(inspectionMapper.selectByClientRequestId("request-1")).thenReturn(null, saved);
        when(packageMapper.selectByIdForUpdate(1L)).thenReturn(unit(1L, "QC_PASSED"));
        when(packageMapper.selectByIdForUpdate(3L)).thenReturn(unit(3L, "QC_PASSED"));
        clearInvocations(inspectionMapper, memberMapper, itemMapper);
        assertEquals(500L, service.createBatch(request()));
        verify(inspectionMapper, times(2)).selectByClientRequestId("request-1");
        verify(inspectionMapper, never()).insert(any(RdQualityInspectionDO.class));
        verifyNoInteractions(memberMapper, itemMapper);
    }

    @Test void shouldConvertClaimUniqueKeyConflictIntoBusinessFailure() {
        stubSourceAndPackages("LABELED");
        when(inspectionMapper.insert(any(RdQualityInspectionDO.class))).thenAnswer(invocation -> {
            invocation.<RdQualityInspectionDO>getArgument(0).setId(500L); return 1;
        });
        when(memberMapper.insert(any(RdQualityInspectionMemberDO.class))).thenThrow(new DuplicateKeyException("active claim"));
        assertError(QUALITY_INSPECTION_PACKAGE_CLAIMED, () -> service.createBatch(request()));
        verifyNoInteractions(itemMapper);
    }

    @Test void shouldSubmitOnlyCoverageWithHeaderBeforeSortedPackages() {
        var inspection = stubLifecycle("DRAFT", "LABELED");
        when(packageMapper.updateById(any(RdPackageUnitDO.class))).thenReturn(1);
        when(inspectionMapper.updateById(any(RdQualityInspectionDO.class))).thenReturn(1);
        service.submit(500L, 100L, "forged name");
        InOrder locks = inOrder(inspectionMapper, packageMapper);
        locks.verify(inspectionMapper).selectByIdForUpdate(500L);
        locks.verify(packageMapper).selectByIdForUpdate(1L);
        locks.verify(packageMapper).selectByIdForUpdate(3L);
        ArgumentCaptor<RdPackageUnitDO> packages = ArgumentCaptor.forClass(RdPackageUnitDO.class);
        verify(packageMapper, times(2)).updateById(packages.capture());
        assertTrue(packages.getAllValues().stream().allMatch(unit -> "QC_PENDING".equals(unit.getStatus())));
        assertEquals("SUBMITTED", inspection.getStatus());
        assertEquals("server-100", inspection.getSubmitterName());
        assertNotNull(inspection.getSubmitTime());
        verify(packageMapper, never()).selectByIdForUpdate(2L);
        verify(memberMapper, never()).releaseActiveByInspectionId(anyLong());
        verifyNoInteractions(outputMapper, productionTaskMapper);
    }

    @Test void shouldValidateAllSubmitStatesBeforeUpdatingAnyMember() {
        stubLifecycle("DRAFT", "LABELED");
        when(packageMapper.selectByIdForUpdate(3L)).thenReturn(unit(3L, "IN_STOCK"));
        assertError(PACKAGE_STATUS_INVALID, () -> service.submit(500L, 100L, "ignored"));
        verify(packageMapper, never()).updateById(any(RdPackageUnitDO.class));
        verify(inspectionMapper, never()).updateById(any(RdQualityInspectionDO.class));
    }

    @Test void shouldRejectTamperedMembershipSnapshotBeforeWriting() {
        stubLifecycle("DRAFT", "LABELED");
        when(packageMapper.selectByIdForUpdate(3L)).thenReturn(unit(3L, "LABELED").setPackageWeight(BigDecimal.TEN));
        assertError(QUALITY_INSPECTION_SOURCE_MISMATCH, () -> service.submit(500L, 100L, "ignored"));
        verify(packageMapper, never()).updateById(any(RdPackageUnitDO.class));
    }

    @Test void shouldRejectMissingOrCorruptedBatchMembersBeforePackageReads() {
        var header = new RdQualityInspectionDO().setId(500L).setPackageId(1L)
                .setClientRequestId("request-1").setStatus("DRAFT");
        when(inspectionMapper.selectByIdForUpdate(500L)).thenReturn(header);
        List<List<RdQualityInspectionMemberDO>> invalid = List.of(List.of(),
                List.of(members().get(0), members().get(0)),
                List.of(members().get(0).setActiveKey(null), members().get(1)),
                List.of(members().get(0).setSampled(false), members().get(1)),
                List.of(members().get(0).setInspectionId(999L), members().get(1)));
        for (var selection : invalid) {
            when(memberMapper.selectListByInspectionId(500L)).thenReturn(selection);
            assertError(QUALITY_INSPECTION_SELECTION_INVALID, () -> service.submit(500L, 100L, "ignored"));
        }
        verifyNoInteractions(packageMapper, itemMapper);
    }

    @Test void shouldRejectMissingSampleMeasurementDuringSubmitAndReview() {
        var header = new RdQualityInspectionDO().setId(500L).setPackageId(1L)
                .setClientRequestId("request-1").setStatus("DRAFT").setSubmitterId(100L);
        when(inspectionMapper.selectByIdForUpdate(500L)).thenReturn(header);
        var members = members();
        members.get(1).setSampled(true);
        when(memberMapper.selectListByInspectionId(500L)).thenReturn(members);
        when(itemMapper.selectListByInspectionId(500L)).thenReturn(List.of(item(1L)));
        assertError(QUALITY_INSPECTION_ITEMS_INVALID, () -> service.submit(500L, 100L, "ignored"));
        header.setStatus("SUBMITTED");
        login(200L);
        assertError(QUALITY_INSPECTION_ITEMS_INVALID, () -> service.approve(500L, 200L, "ignored"));
        assertError(QUALITY_INSPECTION_ITEMS_INVALID, () -> service.reject(500L, 200L, "ignored", "reason"));
        verifyNoInteractions(packageMapper);
        verify(memberMapper, never()).releaseActiveByInspectionId(anyLong());
    }

    @Test void shouldApproveCoverageRecomputeValuesAndReleaseEveryClaim() {
        var inspection = stubLifecycle("SUBMITTED", "QC_PENDING");
        login(200L);
        when(packageMapper.updateById(any(RdPackageUnitDO.class))).thenReturn(1);
        when(inspectionMapper.updateById(any(RdQualityInspectionDO.class))).thenReturn(1);
        when(memberMapper.releaseActiveByInspectionId(500L)).thenReturn(2);
        service.approve(500L, 200L, "ignored");
        assertEquals("PASSED", inspection.getStatus());
        assertEquals("server-200", inspection.getReviewerName());
        ArgumentCaptor<RdPackageUnitDO> units = ArgumentCaptor.forClass(RdPackageUnitDO.class);
        verify(packageMapper, times(2)).updateById(units.capture());
        assertEquals(List.of(1L, 3L), units.getAllValues().stream().map(RdPackageUnitDO::getId).toList());
        assertTrue(units.getAllValues().stream().allMatch(unit -> "QC_PASSED".equals(unit.getStatus())));
        verify(memberMapper).releaseActiveByInspectionId(500L);
    }

    @Test void shouldRejectReviewPackageMismatchBeforeAnyUpdatesOrRelease() {
        stubLifecycle("SUBMITTED", "QC_PENDING");
        login(200L);
        when(packageMapper.selectByIdForUpdate(3L)).thenReturn(unit(3L, "IN_STOCK"));
        assertError(PACKAGE_STATUS_INVALID, () -> service.approve(500L, 200L, "ignored"));
        verify(packageMapper, never()).updateById(any(RdPackageUnitDO.class));
        verify(inspectionMapper, never()).updateById(any(RdQualityInspectionDO.class));
        verify(memberMapper, never()).releaseActiveByInspectionId(anyLong());
    }

    @Test void shouldFailIfOnlySomeClaimsRelease() {
        stubLifecycle("SUBMITTED", "QC_PENDING");
        login(200L);
        when(packageMapper.updateById(any(RdPackageUnitDO.class))).thenReturn(1);
        when(inspectionMapper.updateById(any(RdQualityInspectionDO.class))).thenReturn(1);
        when(memberMapper.releaseActiveByInspectionId(500L)).thenReturn(1);
        assertError(QUALITY_INSPECTION_WRITE_CONFLICT, () -> service.approve(500L, 200L, "ignored"));
    }

    @Test void shouldRejectAndReleaseOnlyCoverageWithRequiredReason() {
        var inspection = stubLifecycle("SUBMITTED", "QC_PENDING");
        login(200L);
        when(packageMapper.updateById(any(RdPackageUnitDO.class))).thenReturn(1);
        when(inspectionMapper.updateById(any(RdQualityInspectionDO.class))).thenReturn(1);
        when(memberMapper.releaseActiveByInspectionId(500L)).thenReturn(2);
        service.reject(500L, 200L, "ignored", "out of range");
        assertEquals("REJECTED", inspection.getStatus());
        assertEquals("out of range", inspection.getRejectReason());
        ArgumentCaptor<RdPackageUnitDO> units = ArgumentCaptor.forClass(RdPackageUnitDO.class);
        verify(packageMapper, times(2)).updateById(units.capture());
        assertTrue(units.getAllValues().stream().allMatch(unit -> "QC_REJECTED".equals(unit.getStatus())));
    }

    @Test void shouldRejectBlankReviewReasonWithoutReadingOrWriting() {
        assertError(QUALITY_INSPECTION_REJECT_REASON_REQUIRED, () -> service.reject(500L, 100L, "ignored", " \t"));
        verifyNoInteractions(inspectionMapper, packageMapper, memberMapper);
    }

    @Test void shouldRejectSelfReviewBeforeReadingMemberOrPackageStores() {
        when(inspectionMapper.selectByIdForUpdate(500L)).thenReturn(new RdQualityInspectionDO()
                .setId(500L).setStatus("SUBMITTED").setSubmitterId(100L));
        assertError(QUALITY_INSPECTION_SELF_REVIEW, () -> service.approve(500L, 100L, "ignored"));
        assertError(QUALITY_INSPECTION_SELF_REVIEW, () -> service.reject(500L, 100L, "ignored", "reason"));
        verifyNoInteractions(memberMapper, packageMapper, itemMapper);
    }

    @Test void shouldRejectUnauthenticatedOrSpoofedServiceActor() {
        assertError(QUALITY_INSPECTION_OPERATOR_INVALID, () -> service.submit(500L, 200L, "forged"));
        SecurityContextHolder.clearContext();
        assertError(QUALITY_INSPECTION_OPERATOR_INVALID, () -> service.createBatch(request()));
        assertError(QUALITY_INSPECTION_OPERATOR_INVALID, () -> service.create(1L, List.of(item(1L)), null));
        verifyNoInteractions(inspectionMapper, packageMapper);
    }

    @ParameterizedTest @ValueSource(strings = {"SUBMITTED", "PASSED", "REJECTED"})
    void shouldReplaySubmitOnlyForOriginalSubmitterWithoutChangingTime(String status) {
        var original = LocalDateTime.of(2026, 1, 1, 8, 0);
        var inspection = new RdQualityInspectionDO().setId(500L).setStatus(status).setSubmitterId(100L).setSubmitTime(original);
        when(inspectionMapper.selectByIdForUpdate(500L)).thenReturn(inspection);
        service.submit(500L, 100L, "ignored");
        assertEquals(original, inspection.getSubmitTime());
        login(200L);
        assertError(QUALITY_INSPECTION_REQUEST_CONFLICT, () -> service.submit(500L, 200L, "ignored"));
        verify(inspectionMapper, never()).updateById(any(RdQualityInspectionDO.class));
        verifyNoInteractions(packageMapper, memberMapper, itemMapper);
    }

    @ParameterizedTest @ValueSource(strings = {"PASSED", "REJECTED"})
    void shouldReplayReviewOnlyForSameActorAndExactResultAndReason(String status) {
        login(200L);
        var time = LocalDateTime.of(2026, 1, 1, 9, 0);
        var inspection = new RdQualityInspectionDO().setId(500L).setStatus(status).setConclusion(status)
                .setReviewerId(200L).setReviewTime(time).setRejectReason("REJECTED".equals(status) ? "original reason" : null);
        when(inspectionMapper.selectByIdForUpdate(500L)).thenReturn(inspection);
        if ("PASSED".equals(status)) {
            service.approve(500L, 200L, "ignored");
            assertError(QUALITY_INSPECTION_REQUEST_CONFLICT, () -> service.reject(500L, 200L, "ignored", "changed result"));
        } else {
            service.reject(500L, 200L, "ignored", "original reason");
            assertError(QUALITY_INSPECTION_REQUEST_CONFLICT, () -> service.reject(500L, 200L, "ignored", "changed reason"));
            assertError(QUALITY_INSPECTION_REQUEST_CONFLICT, () -> service.approve(500L, 200L, "ignored"));
        }
        assertEquals(time, inspection.getReviewTime());
        login(300L);
        assertError(QUALITY_INSPECTION_REQUEST_CONFLICT, () -> service.approve(500L, 300L, "ignored"));
        verify(inspectionMapper, never()).updateById(any(RdQualityInspectionDO.class));
        verifyNoInteractions(packageMapper, memberMapper, itemMapper);
    }

    @Test void shouldPageScopeByActualOutputAndNeverWriteWhileScanning() {
        RdPackageUnitDO representative = unit(1L, "LABELED");
        var req = new RdQualityInspectionScopeReqVO().setPackageCode("https://untrusted.example/pkg/PKG-1?x=y");
        req.setPageNo(2).setPageSize(1);
        when(packageMapper.selectByPackageCode("PKG-1")).thenReturn(representative);
        when(outputMapper.selectById(101L)).thenReturn(output());
        when(packageMapper.selectPage(eq(req), any(LambdaQueryWrapperX.class)))
                .thenReturn(new PageResult<>(List.of(unit(3L, "LABELED")), 3L));
        when(inspectionMapper.selectActiveByPackageId(3L, null)).thenReturn(new RdQualityInspectionDO().setId(900L).setInspectionNo("QC-ACTIVE"));

        var result = service.scope(req);

        assertEquals(3L, result.getPackages().getTotal());
        assertEquals(1L, result.getRepresentative().getId());
        assertTrue(result.getRepresentative().getEligible());
        assertEquals(1, result.getPackages().getList().size());
        assertFalse(result.getPackages().getList().get(0).getEligible());
        assertEquals(900L, result.getPackages().getList().get(0).getActiveInspectionId());
        assertEquals("QC-ACTIVE", result.getPackages().getList().get(0).getActiveInspectionNo());
        ArgumentCaptor<LambdaQueryWrapperX<RdPackageUnitDO>> query = ArgumentCaptor.forClass(LambdaQueryWrapperX.class);
        verify(packageMapper).selectPage(eq(req), query.capture());
        assertTrue(query.getValue().getSqlSegment().contains("production_output_id"));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(101L));
        verify(packageMapper, never()).updateById(any(RdPackageUnitDO.class));
        verifyNoInteractions(memberMapper, itemMapper);
    }

    @Test void shouldReturnOnlyNoSourceRepresentativeAndHonorPageNumber() {
        var unit = unit(1L, "LABELED").setProductionOutputId(null);
        when(packageMapper.selectByPackageCode("PKG-1")).thenReturn(unit);
        var req = new RdQualityInspectionScopeReqVO().setPackageCode("/pkg/PKG-1#fragment");
        var first = service.scope(req);
        assertEquals(1, first.getPackages().getList().size());
        assertTrue(first.getPackages().getList().get(0).getEligible());
        req.setPageNo(2);
        var second = service.scope(req);
        assertEquals(1L, second.getPackages().getTotal());
        assertTrue(second.getPackages().getList().isEmpty());
        assertTrue(second.getRepresentative().getEligible());
        verify(packageMapper, never()).selectPage(any(PageParam.class), any(LambdaQueryWrapperX.class));
        verifyNoInteractions(outputMapper);
    }

    @Test void shouldReturnRealDetailSnapshotsAndLegacyUnknownSampling() {
        when(inspectionMapper.selectById(500L)).thenReturn(new RdQualityInspectionDO().setId(500L).setPackageId(1L));
        when(memberMapper.selectListByInspectionId(500L)).thenReturn(members());
        var detail = service.getDetail(500L);
        assertEquals(2, detail.getCoverageCount());
        assertEquals(1, detail.getSampleCount());
        assertFalse(detail.getMembers().get(1).getSampled());
        assertEquals(new BigDecimal("1000"), detail.getMembers().get(0).getPackageWeight());
        when(memberMapper.selectListByInspectionId(500L)).thenReturn(List.of());
        when(packageMapper.selectById(1L)).thenReturn(unit(1L, "QC_PASSED"));
        var legacy = service.getDetail(500L);
        assertEquals(1, legacy.getCoverageCount());
        assertNull(legacy.getSampleCount());
        assertNull(legacy.getMembers().get(0).getSampled());
        verify(memberMapper, never()).insert(any(RdQualityInspectionMemberDO.class));
    }

    @Test void shouldApplyServerStatusAndInspectionNumberFiltersAndKeepTotal() {
        var req = new RdQualityInspectionPageReqVO().setStatus("SUBMITTED").setInspectionNo("QC-1");
        when(inspectionMapper.selectPage(eq(req), any(LambdaQueryWrapperX.class))).thenReturn(new PageResult<>(List.of(), 47L));
        assertEquals(47L, service.page(req).getTotal());
        ArgumentCaptor<LambdaQueryWrapperX<RdQualityInspectionDO>> query = ArgumentCaptor.forClass(LambdaQueryWrapperX.class);
        verify(inspectionMapper).selectPage(eq(req), query.capture());
        assertTrue(query.getValue().getSqlSegment().contains("status") && query.getValue().getSqlSegment().contains("inspection_no"));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue("SUBMITTED"));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue("%QC-1%"));
    }

    private void stubSource() {
        RdProductionOutputDO output = output().setProductionTaskId(55L);
        when(outputMapper.selectById(101L)).thenReturn(output);
        when(outputMapper.selectByIdForUpdate(101L)).thenReturn(output);
        when(productionTaskMapper.selectByIdForUpdate(55L)).thenReturn(new RdProductionTaskDO().setId(55L));
    }

    private void stubSourceAndPackages(String status) {
        stubSource();
        when(packageMapper.selectById(1L)).thenReturn(unit(1L, status));
        when(packageMapper.selectByIdForUpdate(1L)).thenReturn(unit(1L, status));
        when(packageMapper.selectByIdForUpdate(3L)).thenReturn(unit(3L, status));
    }

    private void stubNewWrites() {
        when(inspectionMapper.insert(any(RdQualityInspectionDO.class))).thenAnswer(invocation -> {
            invocation.<RdQualityInspectionDO>getArgument(0).setId(500L); return 1;
        });
        when(memberMapper.insert(any(RdQualityInspectionMemberDO.class))).thenReturn(1);
        when(itemMapper.insert(any(RdQualityInspectionItemDO.class))).thenReturn(1);
    }

    private RdQualityInspectionDO insertedHeader() {
        ArgumentCaptor<RdQualityInspectionDO> header = ArgumentCaptor.forClass(RdQualityInspectionDO.class);
        verify(inspectionMapper).insert(header.capture());
        return header.getValue();
    }

    private RdQualityInspectionDO stubLifecycle(String inspectionStatus, String packageStatus) {
        var inspection = new RdQualityInspectionDO().setId(500L).setPackageId(1L).setProductionOutputId(101L)
                .setClientRequestId("request-1").setStatus(inspectionStatus).setSubmitterId(100L);
        when(inspectionMapper.selectByIdForUpdate(500L)).thenReturn(inspection);
        when(memberMapper.selectListByInspectionId(500L)).thenReturn(members());
        when(itemMapper.selectListByInspectionId(500L)).thenReturn(List.of(item(1L).setQualified(false)));
        when(packageMapper.selectByIdForUpdate(1L)).thenReturn(unit(1L, packageStatus));
        when(packageMapper.selectByIdForUpdate(3L)).thenReturn(unit(3L, packageStatus));
        return inspection;
    }

    static List<RdQualityInspectionMemberDO> members() {
        return List.of(new RdQualityInspectionMemberDO().setInspectionId(500L).setPackageId(1L)
                        .setPackageCode("PKG-1").setPackageWeight(new BigDecimal("1000")).setSampled(true).setActiveKey(1L),
                new RdQualityInspectionMemberDO().setInspectionId(500L).setPackageId(3L)
                        .setPackageCode("PKG-3").setPackageWeight(new BigDecimal("1000")).setSampled(false).setActiveKey(3L));
    }

    static RdPackageUnitDO unit(Long id, String status) {
        return new RdPackageUnitDO().setId(id).setPackageCode("PKG-" + id).setPackageWeight(new BigDecimal("1000"))
                .setProductionOutputId(101L).setCustomerName("customer").setProductModel("model")
                .setFurnaceNo("F1").setFurnaceLayer("L1").setStatus(status).setVersion(0);
    }

    static RdProductionOutputDO output() {
        return new RdProductionOutputDO().setId(101L).setCustomerName("customer").setProductModel("model")
                .setFurnaceNo("F1").setFurnaceLayer("L1").setStatus("CONFIRMED");
    }

    static RdQualityInspectionItemDO item(Long sampleId) {
        return new RdQualityInspectionItemDO().setSamplePackageId(sampleId).setItemCode("D").setItemName("Density")
                .setMeasuredValue(BigDecimal.ONE).setStandardMin(BigDecimal.ZERO).setStandardMax(BigDecimal.TEN);
    }

    static RdQualityInspectionBatchCreateReqVO request() {
        return new RdQualityInspectionBatchCreateReqVO().setPackageId(1L).setPackageIds(List.of(3L, 1L))
                .setSamplePackageIds(List.of(1L)).setItems(List.of(requestItem())).setClientRequestId("request-1");
    }

    static RdQualityInspectionItemReqVO requestItem() {
        return new RdQualityInspectionItemReqVO().setSamplePackageId(1L).setItemCode("D").setItemName("Density")
                .setMeasuredValue(BigDecimal.ONE).setStandardMin(BigDecimal.ZERO).setStandardMax(BigDecimal.TEN);
    }

    static void login(Long id) {
        var user = new LoginUser().setId(id).setInfo(Map.of("nickname", "server-" + id));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null));
    }

    private static void assertError(ErrorCode expected, Runnable action) {
        ServiceException error = assertThrows(ServiceException.class, action::run);
        assertEquals(expected.getCode(), error.getCode());
    }
}
