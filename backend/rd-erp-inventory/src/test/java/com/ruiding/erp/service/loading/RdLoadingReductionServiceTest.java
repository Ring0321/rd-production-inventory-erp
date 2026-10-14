package com.ruiding.erp.service.loading;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import com.ruiding.erp.controller.admin.loading.vo.*;
import com.ruiding.erp.dal.dataobject.loading.*;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.mysql.loading.*;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RdLoadingReductionServiceTest {
    @Mock RdLoadingReductionMapper reductionMapper;
    @Mock RdLoadingTaskMapper taskMapper;
    @Mock RdLoadingTaskLineMapper lineMapper;
    @Mock RdPickupSlipMapper slipMapper;
    @Mock RdPickupSlipLineMapper slipLineMapper;
    @Mock RdLoadingPackageClaimMapper claimMapper;
    @Mock RdLoadingExceptionMapper exceptionMapper;
    @Mock RdPackageUnitMapper packageMapper;
    @Mock PermissionService permissionService;
    @InjectMocks RdLoadingReductionServiceImpl service;
    RdLoadingTaskDO task;
    RdPickupSlipDO slip;
    List<RdLoadingTaskLineDO> lines;
    final java.util.ArrayList<RdPackageUnitDO> units = new java.util.ArrayList<>();
    final java.util.ArrayList<RdLoadingPackageClaimDO> claims = new java.util.ArrayList<>();
    RdLoadingReductionDO saved;

    @BeforeEach void fixture() {
        TenantContextHolder.setTenantId(1L);
        task = new RdLoadingTaskDO(); task.setId(9L); task.setTenantId(1L);
        task.setTaskNo("TASK-LOCAL"); task.setPickupSlipId(7L); task.setPickupSlipNo("SLIP-LOCAL");
        task.setStatus("EXCEPTION_PENDING"); task.setCustomerName("本地测试客户");
        task.setVersion(4); task.setPlanVersion(2); task.setPlannedPackageCount(3);
        task.setPlannedWeight(new BigDecimal("2800"));
        slip = new RdPickupSlipDO(); slip.setId(7L); slip.setTenantId(1L);
        slip.setVersion(1); slip.setStatus("DISPATCHED"); slip.setCustomerName(task.getCustomerName());
        slip.setRequestedPackageCount(3); slip.setRequestedWeight(task.getPlannedWeight());
        lines = List.of(line(11L, "1000"), line(12L, "800"), line(13L, "1000"));
        lenient().when(taskMapper.selectByIdForUpdate(9L)).thenReturn(task);
        lenient().when(taskMapper.selectById(9L)).thenReturn(task);
        lenient().when(slipMapper.selectByIdForUpdate(7L, 1L)).thenReturn(slip);
        lenient().when(slipMapper.selectById(7L)).thenReturn(slip);
        lenient().when(lineMapper.selectListByTaskIdForUpdate(9L)).thenReturn(lines);
        lenient().when(lineMapper.selectListByTaskId(9L)).thenReturn(lines);
        lenient().when(exceptionMapper.selectOpenListByTaskIdForUpdate(9L)).thenReturn(List.of(new RdLoadingExceptionDO()));
        var pickupLine = new RdPickupSlipLineDO(); pickupLine.setId(71L); pickupLine.setSlipId(7L); pickupLine.setTenantId(1L);
        pickupLine.setProductModel("LOCAL-M"); pickupLine.setFurnaceNo("LOCAL-F"); pickupLine.setFurnaceLayer("下层");
        pickupLine.setRequestedPackageCount(3); pickupLine.setRequestedWeight(task.getPlannedWeight());
        lenient().when(slipLineMapper.selectListBySlipId(7L)).thenReturn(List.of(pickupLine));
        lenient().when(reductionMapper.selectTaskListForUpdate(9L, 1L)).thenReturn(List.of());
        lenient().when(permissionService.hasAnyPermissions(20L, "rd:loading-task:dispatch")).thenReturn(true);
        lenient().when(permissionService.hasAnyPermissions(20L, "rd:pickup-slip:approve-reduction")).thenReturn(true);
        lenient().when(permissionService.hasAnyPermissions(30L, "rd:pickup-slip:approve-reduction")).thenReturn(true);
        lenient().when(permissionService.hasAnyPermissions(20L, "rd:loading-task:query", "rd:pickup-slip:query")).thenReturn(true);
        lenient().when(permissionService.hasAnyRoles(20L, "rd_warehouse", "super_admin")).thenReturn(true);
        lenient().when(permissionService.hasAnyRoles(30L, "rd_sales", "super_admin")).thenReturn(true);
        lenient().when(permissionService.hasAnyRoles(20L, "rd_forklift_driver")).thenReturn(false);
        lenient().when(permissionService.hasAnyRoles(30L, "rd_forklift_driver")).thenReturn(false);
        lenient().doAnswer(inv -> { saved = inv.getArgument(0); saved.setId(50L); return 1; })
                .when(reductionMapper).insert(any(RdLoadingReductionDO.class));
        for (RdLoadingTaskLineDO line : lines) {
            RdPackageUnitDO unit = new RdPackageUnitDO();
            unit.setId(line.getPackageId()); unit.setTenantId(1L); unit.setStatus("IN_STOCK");
            unit.setPackageCode(line.getPackageCode()); unit.setPackageWeight(line.getPackageWeight());
            unit.setStockLotId(line.getStockLotId()); unit.setLocationId(line.getPlannedLocationId());
            unit.setCustomerName(line.getCustomerName()); unit.setProductModel(line.getProductModel());
            unit.setFurnaceNo(line.getFurnaceNo()); unit.setFurnaceLayer(line.getFurnaceLayer());
            unit.setVersion(0); units.add(unit);
            lenient().when(packageMapper.selectByIdForUpdate(line.getPackageId())).thenReturn(unit);
            RdLoadingPackageClaimDO claim = new RdLoadingPackageClaimDO();
            claim.setTenantId(1L); claim.setTaskId(9L); claim.setTaskLineId(line.getId()); claim.setPackageId(line.getPackageId());
            claim.setId(line.getId() + 500); claims.add(claim);
            lenient().when(claimMapper.selectByPackageIdForUpdate(line.getPackageId())).thenReturn(claim);
        }
        lenient().when(packageMapper.selectBatchIds(anyCollection())).thenReturn(units);
        lenient().when(claimMapper.selectTaskList(9L, 1L)).thenReturn(claims);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void createsExactSnapshotWithoutChangingGoodsOrTask() {
        RdLoadingReductionRespVO result = service.create(create(12L), 20L, "仓管");
        assertEquals(50L, result.getId()); assertEquals("PENDING", result.getEffectiveStatus());
        assertEquals(3, result.getOriginalPackageCount()); assertEquals(1, result.getReducedPackageCount());
        assertEquals(2, result.getRemainingPackageCount());
        assertEquals(0, new BigDecimal("800").compareTo(result.getReducedWeight()));
        assertEquals(0, new BigDecimal("2000").compareTo(result.getRemainingWeight()));
        assertEquals(List.of(12L), result.getLines().stream().filter(RdLoadingReductionRespVO.Line::isRemoved).map(RdLoadingReductionRespVO.Line::getId).toList());
        assertNoGoodsWrites();
    }
    @Test void duplicateSameKeyRecoversEvenAfterTaskChanges() {
        service.create(create(12L), 20L, "仓管");
        when(reductionMapper.selectByKeyForUpdate(9L, 1L, "local-reduction-key0001")).thenReturn(saved);
        task.setVersion(5);
        assertEquals(50L, service.create(create(12L), 20L, "仓管").getId());
        verify(reductionMapper, times(1)).insert(any(RdLoadingReductionDO.class));
    }
    @Test void rejectsKeyReusedWithDifferentMembers() {
        service.create(create(12L), 20L, "仓管");
        when(reductionMapper.selectByKeyForUpdate(9L, 1L, "local-reduction-key0001")).thenReturn(saved);
        assertThrows(ServiceException.class, () -> service.create(create(11L), 20L, "仓管"));
    }
    @Test void rejectsScannedMember() {
        lines.get(1).setLineStatus("SCANNED");
        assertThrows(ServiceException.class, () -> service.create(create(12L), 20L, "仓管"));
        verify(reductionMapper, never()).insert(any(RdLoadingReductionDO.class));
    }
    @Test void cannotRemovePreviouslyScannedMemberMarkedAsException() {
        lines.get(1).setLineStatus("EXCEPTION"); lines.get(1).setScanTime(java.time.LocalDateTime.now());
        assertEquals(1080530024, assertThrows(ServiceException.class,
                () -> service.create(create(12L), 20L, "仓管")).getCode());
    }
    @Test void rejectsAllMembersAndDuplicates() {
        assertThrows(ServiceException.class, () -> service.create(create(11L, 12L, 13L), 20L, "仓管"));
        assertThrows(ServiceException.class, () -> service.create(create(12L, 12L), 20L, "仓管"));
    }
    @Test void rejectsWrongTaskAndStaleVersion() {
        assertThrows(ServiceException.class, () -> service.create(create(99L), 20L, "仓管"));
        task.setVersion(5);
        assertThrows(ServiceException.class, () -> service.create(create(12L), 20L, "仓管"));
    }
    @Test void rejectsDriverEvenWithAccidentallyGrantedPermission() {
        when(permissionService.hasAnyRoles(20L, "rd_forklift_driver")).thenReturn(true);
        assertThrows(ServiceException.class, () -> service.create(create(12L), 20L, "司机"));
        verifyNoInteractions(taskMapper);
    }
    @Test void salesApprovesAndDuplicateReviewRecoversButNoGoodsChange() {
        prepareReview();
        RdLoadingReductionRespVO result = service.review(review("APPROVED"), 30L, "商务");
        assertEquals("APPROVED", result.getStatus()); assertEquals(30L, result.getReviewerId());
        assertEquals(1, result.getVersion());
        assertEquals("APPROVED", service.review(review("APPROVED"), 30L, "商务").getStatus());
        verify(reductionMapper, times(1)).updateById(any(RdLoadingReductionDO.class));
        assertNoGoodsWrites();
    }
    @Test void cannotApproveOwnProposal() {
        prepareReview();
        when(permissionService.hasAnyRoles(20L, "rd_sales", "super_admin")).thenReturn(true);
        assertEquals(1080530029, assertThrows(ServiceException.class, () -> service.review(review("APPROVED"), 20L, "仓管")).getCode());
    }
    @Test void rejectsApprovalAfterUnversionedLineChange() {
        prepareReview(); lines.get(1).setPackageWeight(new BigDecimal("700"));
        assertThrows(ServiceException.class, () -> service.review(review("APPROVED"), 30L, "商务"));
        verify(reductionMapper, never()).updateById(any(RdLoadingReductionDO.class));
    }
    @Test void rejectsApprovalAfterSlipChanged() {
        prepareReview(); slip.setVersion(2);
        assertThrows(ServiceException.class, () -> service.review(review("APPROVED"), 30L, "商务"));
    }
    @Test void canRejectStaleRequestButNotOverwriteDecision() {
        prepareReview(); task.setVersion(5);
        assertEquals("REJECTED", service.review(review("REJECTED"), 30L, "商务").getStatus());
        assertThrows(ServiceException.class, () -> service.review(review("APPROVED"), 30L, "商务"));
    }
    @Test void listsExplicitContextAndShowsStaleness() {
        service.create(create(12L), 20L, "仓管"); task.setVersion(5);
        when(reductionMapper.selectTaskList(9L, 1L)).thenReturn(List.of(saved));
        assertEquals("STALE", service.list(9L, null, 20L).get(0).getEffectiveStatus());
        assertThrows(ServiceException.class, () -> service.list(null, null, 20L));
        assertThrows(ServiceException.class, () -> service.list(9L, 7L, 20L));
    }
    @Test void rejectsCrossTenantTask() {
        task.setTenantId(2L);
        assertThrows(ServiceException.class, () -> service.create(create(12L), 20L, "仓管"));
        verifyNoInteractions(reductionMapper);
    }
    @Test void preservesSixDecimalRegisteredWeight() {
        BigDecimal weight = new BigDecimal("800.123456");
        lines.get(1).setPackageWeight(weight);
        packageMapper.selectByIdForUpdate(112L).setPackageWeight(weight);
        task.setPlannedWeight(new BigDecimal("2800.123456")); slip.setRequestedWeight(task.getPlannedWeight());
        slipLineMapper.selectListBySlipId(7L).get(0).setRequestedWeight(task.getPlannedWeight());
        var result = service.create(create(12L), 20L, "仓管");
        assertEquals(0, weight.compareTo(result.getReducedWeight()));
        assertEquals(0, new BigDecimal("2000").compareTo(result.getRemainingWeight()));
    }
    @Test void cannotCreateSecondActiveProposalWithAnotherKey() {
        service.create(create(12L), 20L, "仓管");
        when(reductionMapper.selectTaskListForUpdate(9L, 1L)).thenReturn(List.of(saved));
        var req = create(11L); req.setIdempotencyKey("local-reduction-key0002");
        assertEquals(1080530027, assertThrows(ServiceException.class, () -> service.create(req, 20L, "仓管")).getCode());
    }
    @Test void cannotApproveWhenCurrentPackageLeftStock() {
        prepareReview(); packageMapper.selectByIdForUpdate(112L).setStatus("OUTBOUNDED");
        assertEquals(1080530025, assertThrows(ServiceException.class,
                () -> service.review(review("APPROVED"), 30L, "商务")).getCode());
        verify(reductionMapper, never()).updateById(any(RdLoadingReductionDO.class));
    }
    @Test void cannotApproveWhenPackageClaimBelongsToOtherTask() {
        prepareReview(); claimMapper.selectByPackageIdForUpdate(112L).setTaskId(99L);
        assertEquals(1080530025, assertThrows(ServiceException.class,
                () -> service.review(review("APPROVED"), 30L, "商务")).getCode());
    }
    @Test void reviewRequiresIndependentPermissionAndCurrentVersion() {
        prepareReview(); when(permissionService.hasAnyPermissions(20L, "rd:pickup-slip:approve-reduction")).thenReturn(false);
        assertEquals(1080530031, assertThrows(ServiceException.class,
                () -> service.review(review("APPROVED"), 20L, "仓管")).getCode());
        var req = review("APPROVED"); req.setVersion(99);
        assertEquals(1080530030, assertThrows(ServiceException.class,
                () -> service.review(req, 30L, "商务")).getCode());
    }
    @Test void requiresReasonAndLimitedNonEmptySelection() {
        var req = create(12L); req.setReason(" ");
        assertEquals(1080530024, assertThrows(ServiceException.class, () -> service.create(req, 20L, "仓管")).getCode());
        req.setReason("真实原因"); req.setTaskLineIds(java.util.stream.LongStream.range(1, 102).boxed().toList());
        assertEquals(1080530024, assertThrows(ServiceException.class, () -> service.create(req, 20L, "仓管")).getCode());
    }
    @Test void responseDoesNotExposeInternalDigestsOrRawSnapshot() {
        var result = service.create(create(12L), 20L, "仓管");
        String json = cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(result);
        assertFalse(json.contains("requestDigest")); assertFalse(json.contains("planLinesJson"));
        assertFalse(json.contains("planDigest")); assertFalse(json.contains("slipDigest")); assertTrue(json.contains("lines"));
    }
    @Test void rejectsProposalWhenPickupLineDoesNotMatchEvenWithSameTotal() {
        slipLineMapper.selectListBySlipId(7L).get(0).setProductModel("DIFFERENT");
        assertEquals(1080530025, assertThrows(ServiceException.class,
                () -> service.create(create(12L), 20L, "仓管")).getCode());
    }
    @Test void auditCannotProposeWithLegacyDispatchPermission() {
        when(permissionService.hasAnyRoles(20L, "rd_warehouse", "super_admin")).thenReturn(false);
        assertEquals(1080530031, assertThrows(ServiceException.class,
                () -> service.create(create(12L), 20L, "审计")).getCode());
    }
    @Test void packageVersionChangeMakesApprovalStaleEvenIfValuesReturn() {
        prepareReview(); units.get(1).setVersion(2);
        assertEquals(1080530025, assertThrows(ServiceException.class,
                () -> service.review(review("APPROVED"), 30L, "商务")).getCode());
    }
    @Test void historicalApprovalIsNotEffectiveAfterPackageLeavesStock() {
        prepareReview(); service.review(review("APPROVED"), 30L, "商务"); units.get(1).setStatus("OUTBOUNDED");
        when(reductionMapper.selectTaskList(9L, 1L)).thenReturn(List.of(saved));
        assertEquals("STALE", service.list(9L, null, 20L).get(0).getEffectiveStatus());
        var replay = service.review(review("APPROVED"), 30L, "商务");
        assertEquals("APPROVED", replay.getStatus()); assertEquals("STALE", replay.getEffectiveStatus());
    }
    @Test void exactReadRecoversByKeyOrIdOutsideListWindowWithoutWrite() {
        service.create(create(12L), 20L, "仓管");
        when(reductionMapper.selectByKey(9L, 1L, "local-reduction-key0001")).thenReturn(saved);
        when(reductionMapper.selectById(50L)).thenReturn(saved);
        assertEquals(50L, service.get(null, 9L, "local-reduction-key0001", 20L).getId());
        assertEquals(50L, service.get(50L, null, null, 20L).getId());
        assertNull(service.get(99L, null, null, 20L));
        assertThrows(ServiceException.class, () -> service.get(50L, 9L, null, 20L));
        verify(reductionMapper, never()).updateById(any(RdLoadingReductionDO.class));
    }
    @Test void responseWeightsAreDecimalStringsNotLossyJavascriptNumbers() {
        var result = service.create(create(12L), 20L, "仓管");
        result.setOriginalWeight(new BigDecimal("999999999999.123456"));
        String json = cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(result);
        assertTrue(json.contains("\"originalWeight\":\"999999999999.123456\""));
        assertTrue(json.contains("\"packageWeight\":\"800\""));
    }
    @Test void physicallyStaleApprovalCannotBlockFreshProposalForCurrentPackageVersions() {
        prepareReview(); service.review(review("APPROVED"), 30L, "商务");
        var historical = saved; units.get(1).setVersion(2);
        when(reductionMapper.selectTaskListForUpdate(9L, 1L)).thenReturn(List.of(historical));
        var fresh = create(12L); fresh.setIdempotencyKey("local-reduction-key0002");
        assertEquals("PENDING", service.create(fresh, 20L, "仓管").getStatus());
        verify(reductionMapper, times(2)).insert(any(RdLoadingReductionDO.class));
    }
    private void prepareReview() {
        service.create(create(12L), 20L, "仓管");
        when(reductionMapper.selectById(50L)).thenReturn(saved);
        when(reductionMapper.selectByIdForUpdate(50L, 1L)).thenReturn(saved);
    }
    private void assertNoGoodsWrites() {
        verify(taskMapper, never()).updateById(any(RdLoadingTaskDO.class));
        verify(lineMapper, never()).updateById(any(RdLoadingTaskLineDO.class));
        verify(slipMapper, never()).updateById(any(RdPickupSlipDO.class));
        verify(packageMapper, never()).updateById(any(RdPackageUnitDO.class));
        verify(claimMapper, never()).deleteById(anyLong());
    }
    private static RdLoadingReductionCreateReqVO create(Long... ids) {
        var req = new RdLoadingReductionCreateReqVO(); req.setTaskId(9L); req.setTaskVersion(4);
        req.setTaskLineIds(List.of(ids)); req.setReason("本地核定少装，非现场数据");
        req.setIdempotencyKey("local-reduction-key0001"); return req;
    }
    private static RdLoadingReductionReviewReqVO review(String decision) {
        var req = new RdLoadingReductionReviewReqVO(); req.setId(50L); req.setVersion(0);
        req.setDecision(decision); req.setReviewRemark("商务核实现场申请"); return req;
    }
    private static RdLoadingTaskLineDO line(Long id, String weight) {
        var line = new RdLoadingTaskLineDO(); line.setId(id); line.setTaskId(9L); line.setTenantId(1L);
        line.setLineNo(id.intValue()); line.setPackageId(id + 100); line.setPackageCode("LOCAL-" + id);
        line.setPickupSlipLineId(71L); line.setPlannedLocationId(81L); line.setPlannedLocationCode("LOCAL-A");
        line.setStockLotId(91L); line.setCustomerName("本地测试客户"); line.setProductModel("LOCAL-M");
        line.setFurnaceNo("LOCAL-F"); line.setFurnaceLayer("下层"); line.setPackageWeight(new BigDecimal(weight));
        line.setLineStatus("PLANNED"); line.setPlanVersion(2); line.setVersion(0); return line;
    }
}
