package com.ruiding.erp.service.loading;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import com.ruiding.erp.controller.admin.loading.RdLoadingDispatchController;
import com.ruiding.erp.controller.admin.loading.vo.RdLoadingBatchScanConfirmReqVO;
import com.ruiding.erp.controller.admin.loading.vo.RdLoadingConfirmOutboundReqVO;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingScanRecordDO;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingTaskDO;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingTaskLineDO;
import com.ruiding.erp.dal.dataobject.outbound.RdOutboundOrderDO;
import com.ruiding.erp.dal.dataobject.outbound.RdOutboundOrderLineDO;
import com.ruiding.erp.dal.mysql.loading.*;
import com.ruiding.erp.dal.mysql.outbound.RdOutboundOrderLineMapper;
import com.ruiding.erp.dal.mysql.outbound.RdOutboundOrderMapper;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.dal.mysql.stock.RdStockLotMapper;
import com.ruiding.erp.service.outbound.RdOutboundOrderService;
import com.ruiding.erp.service.packageunit.RdPackageTaskHistoryService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.security.access.prepost.PreAuthorize;

import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static com.ruiding.erp.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RdLoadingIntegrityTest {
    @Mock private RdLoadingTaskMapper taskMapper;
    @Mock private RdLoadingTaskLineMapper taskLineMapper;
    @Mock private RdLoadingScanRecordMapper scanRecordMapper;
    @Mock private RdLoadingExceptionMapper exceptionMapper;
    @Mock private RdLoadingPackageClaimMapper claimMapper;
    @Mock private RdPackageUnitMapper packageMapper;
    @Mock private RdStockLotMapper stockLotMapper;
    @Mock private RdOutboundOrderMapper outboundOrderMapper;
    @Mock private RdOutboundOrderLineMapper outboundOrderLineMapper;
    @Mock private RdOutboundOrderService outboundOrderService;
    @Mock private RdPackageTaskHistoryService taskHistoryService;
    @InjectMocks private RdLoadingDispatchServiceImpl service;

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll static void openValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll static void closeValidator() { validatorFactory.close(); }

    @Test void shouldRequireBatchVersionAtRequestBoundary() {
        var req = batchRequest();
        setBatchVersion(req, null);
        assertTrue(invalidProperties(req).contains("version"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"representativePackageCode", "idempotencyKey", "deviceCode"})
    void shouldEnforceStorageFieldLimits(String property) {
        var req = batchRequest();
        var bean = new BeanWrapperImpl(req);
        int limit = property.equals("deviceCode") ? 64 : 128;
        bean.setPropertyValue(property, "x".repeat(limit));
        assertTrue(invalidProperties(req).isEmpty());
        bean.setPropertyValue(property, "x".repeat(limit + 1));
        assertTrue(invalidProperties(req).contains(property));
    }

    @Test void shouldRejectNonDistinctBatchIdsAtRequestBoundary() {
        var req = batchRequest();
        req.setTaskLineIds(List.of(600L, 600L));
        assertFalse(invalidProperties(req).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void shouldRejectNonPositiveBatchIdsAtRequestBoundary(long id) {
        var req = batchRequest();
        req.setTaskLineIds(List.of(id));
        assertFalse(invalidProperties(req).isEmpty());
    }

    @Test void shouldRejectNullBatchIdsAtRequestBoundary() {
        var req = batchRequest();
        req.setTaskLineIds(Arrays.asList(600L, null));
        assertFalse(invalidProperties(req).isEmpty());
    }

    @Test void shouldEnforceOneToFiveHundredBatchIdsAtRequestBoundary() {
        var req = batchRequest();
        req.setTaskLineIds(List.of());
        assertFalse(invalidProperties(req).isEmpty());
        req.setTaskLineIds(List.of(600L));
        assertTrue(invalidProperties(req).isEmpty());
        req.setTaskLineIds(LongStream.rangeClosed(1, 500).boxed().toList());
        assertTrue(invalidProperties(req).isEmpty());
        req.setTaskLineIds(LongStream.rangeClosed(1, 501).boxed().toList());
        assertFalse(invalidProperties(req).isEmpty());
    }

    @Test void shouldRejectMissingBatchVersionInService() {
        setupBatch();
        var req = batchRequest();
        setBatchVersion(req, null);
        assertCode(LOADING_TASK_VERSION_CONFLICT.getCode(),
                () -> service.confirmBatchScan(req, 20L, "warehouse"));
        verifyNoWrites();
    }

    @Test void shouldRejectUncommittedOldSelectionAfterReplacementRetainsLineId() {
        var task = setupBatch();
        task.setVersion(3);
        task.setPlanVersion(2);
        var unchanged = line(600L, "PK600", "PLANNED", "1000");
        var replaced = line(601L, "PK-NEW", "PLANNED", "1000");
        replaced.setPackageId(999L);
        lenient().when(taskLineMapper.selectListByTaskIdForUpdate(500L)).thenReturn(List.of(unchanged, replaced));
        var req = batchRequest();
        setBatchVersion(req, 2);

        assertCode(LOADING_TASK_VERSION_CONFLICT.getCode(),
                () -> service.confirmBatchScan(req, 20L, "warehouse"));

        assertEquals("PLANNED", replaced.getLineStatus());
        verify(taskLineMapper, never()).selectListByTaskIdForUpdate(500L);
        verifyNoWrites();
    }

    @Test void shouldCheckNewBatchVersionBeforeStatusAndLines() {
        var task = task("EXCEPTION_PENDING", 3);
        when(taskMapper.selectByIdForUpdate(500L)).thenReturn(task);
        assertCode(LOADING_TASK_VERSION_CONFLICT.getCode(),
                () -> service.confirmBatchScan(batchRequest(), 20L, "warehouse"));
        verifyNoInteractions(taskLineMapper, taskHistoryService);
        verifyNoWrites();
    }

    @ParameterizedTest
    @ValueSource(strings = {"actor", "selection", "representative", "version"})
    void shouldRejectChangedActorOrContentForCommittedKey(String change) {
        setupBatch();
        var saved = rememberScanRecord();
        service.confirmBatchScan(batchRequest(), 20L, "warehouse");
        assertNotNull(saved.get());
        clearInvocations(taskMapper, taskLineMapper, scanRecordMapper, taskHistoryService);
        var replay = batchRequest();
        long actor = 20L;
        if (change.equals("actor")) actor = 21L;
        if (change.equals("selection")) replay.setTaskLineIds(List.of(600L));
        if (change.equals("representative")) replay.setRepresentativePackageCode("PK601");
        if (change.equals("version")) setBatchVersion(replay, 1);
        long replayActor = actor;

        assertCode(1_080_530_023,
                () -> service.confirmBatchScan(replay, replayActor, "warehouse"));

        verifyNoWrites();
    }

    @Test void shouldReplayNormalizedOriginalContentBeforeCurrentVersionOrStatus() {
        var task = setupBatch();
        var saved = rememberScanRecord();
        var first = service.confirmBatchScan(batchRequest(), 20L, "warehouse");
        task.setVersion(9);
        task.setStatus("EXCEPTION_PENDING");
        clearInvocations(taskMapper, taskLineMapper, scanRecordMapper, taskHistoryService);
        var replay = batchRequest();
        replay.setTaskLineIds(List.of(601L, 600L));
        replay.setRepresentativePackageCode(" https://id.tjldsic.com/pkg/PK600?source=pda#scan ");
        replay.setDeviceCode("another-device");
        replay.setDeviceTime(LocalDateTime.of(2026, 10, 11, 12, 0));

        var result = service.confirmBatchScan(replay, 20L, "renamed-warehouse");

        assertTrue(result.getSuccess());
        assertTrue(result.getReplayed());
        assertEquals(first.getRecordId(), result.getRecordId());
        assertEquals("PK600", result.getRepresentativePackageCode());
        assertEquals(0, result.getConfirmedCount());
        assertEquals(BigDecimal.ZERO, result.getConfirmedWeight());
        assertTrue(saved.get().getRequestDigest().matches("[0-9a-f]{64}"));
        assertEquals(20L, saved.get().getOperatorId());
        verifyNoWrites();
    }

    @Test void shouldRefuseHistoricalReplayWithoutInventingItsSelection() {
        var historical = new RdLoadingScanRecordDO();
        historical.setId(88L);
        historical.setTaskId(500L);
        historical.setTaskLineId(600L);
        historical.setPackageCode("PK600");
        historical.setOperatorId(20L);
        historical.setSuccess(true);
        historical.setScanResult("BATCH_CONFIRMED");
        when(taskMapper.selectByIdForUpdate(500L)).thenReturn(task("PENDING_REVIEW", 1));
        when(scanRecordMapper.selectByTaskAndIdempotencyKey(500L, "batch-one")).thenReturn(historical);

        assertCode(1_080_530_023, () -> service.confirmBatchScan(batchRequest(), 20L, "warehouse"));

        verifyNoInteractions(taskLineMapper, taskHistoryService);
        verifyNoWrites();
    }

    @Test void shouldRequireOutboundVersionAtRequestBoundary() {
        var req = outboundRequest(null);
        assertTrue(invalidProperties(req).contains("version"));
    }

    @Test void shouldRejectMissingVersionForFirstOutboundExecution() {
        when(taskMapper.selectByIdForUpdate(500L)).thenReturn(task("PENDING_REVIEW", 1));
        assertCode(LOADING_TASK_VERSION_CONFLICT.getCode(),
                () -> service.confirmOutbound(outboundRequest(null), 20L, "warehouse"));
        verifyNoInteractions(exceptionMapper, taskLineMapper, outboundOrderMapper, outboundOrderService);
        verifyNoWrites();
    }

    @Test void shouldRejectStaleVersionForFirstOutboundExecution() {
        when(taskMapper.selectByIdForUpdate(500L)).thenReturn(task("PENDING_REVIEW", 2));
        assertCode(LOADING_TASK_VERSION_CONFLICT.getCode(),
                () -> service.confirmOutbound(outboundRequest(1), 20L, "warehouse"));
        verifyNoInteractions(exceptionMapper, taskLineMapper, outboundOrderMapper, outboundOrderService);
    }

    @ParameterizedTest
    @ValueSource(ints = {7, 8})
    void shouldRecoverCompletedOutboundFromPersistedRelationsWithoutWrites(int requestVersion) {
        var task = setupCompletedOutbound();
        var confirmedTime = task.getConfirmTime();

        var result = service.confirmOutbound(outboundRequest(requestVersion), 21L, "another-dispatcher");

        assertEquals(500L, result.getTaskId());
        assertEquals("LD-500", result.getTaskNo());
        assertEquals(List.of(900L, 901L), result.getOutboundOrderIds());
        assertEquals(3, result.getOutboundPackageCount());
        assertEquals(new BigDecimal("2100"), result.getOutboundWeight());
        assertEquals(8, task.getVersion());
        assertEquals(99, task.getScannedPackageCount());
        assertEquals(new BigDecimal("99999"), task.getScannedWeight());
        assertEquals(confirmedTime, task.getConfirmTime());
        verifyNoInteractions(exceptionMapper, claimMapper, packageMapper, stockLotMapper, outboundOrderService);
        verifyNoWrites();
    }

    @Test void shouldNotFabricateCompletedResponseWhenPersistedOrderIsMissing() {
        setupCompletedOutbound();
        lenient().when(outboundOrderMapper.selectById(900L)).thenReturn(null);
        assertCode(OUTBOUND_ORDER_STATUS_INVALID.getCode(),
                () -> service.confirmOutbound(outboundRequest(7), 20L, "warehouse"));
        verifyNoWrites();
    }

    @Test void shouldNotFabricateCompletedResponseWhenLineRelationIsWrong() {
        setupCompletedOutbound();
        var wrong = postedLine(1601L, 900L, 999L, 2601L, "700");
        lenient().when(outboundOrderLineMapper.selectListByOrderId(900L)).thenReturn(List.of(wrong));
        assertCode(OUTBOUND_ORDER_STATUS_INVALID.getCode(),
                () -> service.confirmOutbound(outboundRequest(7), 20L, "warehouse"));
        verifyNoWrites();
    }

    @Test void shouldKeepDispatchPermissionOnBothConfirmationEndpoints() throws Exception {
        for (var type : List.of(RdLoadingBatchScanConfirmReqVO.class, RdLoadingConfirmOutboundReqVO.class)) {
            String method = type == RdLoadingBatchScanConfirmReqVO.class ? "confirmBatchScan" : "confirmOutbound";
            var permission = RdLoadingDispatchController.class.getMethod(method, type).getAnnotation(PreAuthorize.class);
            assertNotNull(permission);
            assertEquals("@ss.hasPermission('rd:loading-task:dispatch')", permission.value());
        }
    }

    private RdLoadingTaskDO setupBatch() {
        var task = task("ASSIGNED", 0);
        lenient().when(taskMapper.selectByIdForUpdate(500L)).thenReturn(task);
        var lines = List.of(line(600L, "PK600", "PLANNED", "1000"),
                line(601L, "PK601", "PLANNED", "1000"));
        lenient().when(taskLineMapper.selectListByTaskIdForUpdate(500L)).thenReturn(lines);
        lenient().when(taskLineMapper.selectListByTaskId(500L)).thenReturn(lines);
        return task;
    }

    private AtomicReference<RdLoadingScanRecordDO> rememberScanRecord() {
        var saved = new AtomicReference<RdLoadingScanRecordDO>();
        when(scanRecordMapper.selectByTaskAndIdempotencyKey(500L, "batch-one")).thenAnswer(inv -> saved.get());
        doAnswer(inv -> {
            RdLoadingScanRecordDO record = inv.getArgument(0);
            record.setId(88L);
            saved.set(record);
            return 1;
        }).when(scanRecordMapper).insert(any(RdLoadingScanRecordDO.class));
        return saved;
    }

    private RdLoadingTaskDO setupCompletedOutbound() {
        var task = task("OUTBOUNDED", 8);
        task.setScannedPackageCount(99);
        task.setScannedWeight(new BigDecimal("99999"));
        task.setConfirmTime(LocalDateTime.of(2026, 10, 11, 10, 30));
        when(taskMapper.selectByIdForUpdate(500L)).thenReturn(task);
        var first = completedLine(600L, 901L, "600");
        var second = completedLine(601L, 900L, "700");
        var third = completedLine(602L, 901L, "800");
        lenient().when(taskLineMapper.selectListByTaskId(500L)).thenReturn(List.of(third, first, second));
        lenient().when(outboundOrderMapper.selectById(900L)).thenReturn(postedOrder(900L));
        lenient().when(outboundOrderMapper.selectById(901L)).thenReturn(postedOrder(901L));
        lenient().when(outboundOrderLineMapper.selectListByOrderId(900L)).thenReturn(List.of(
                postedLine(second.getOutboundOrderLineId(), 900L, second.getPackageId(), second.getStockTransactionId(), "700")));
        lenient().when(outboundOrderLineMapper.selectListByOrderId(901L)).thenReturn(List.of(
                postedLine(first.getOutboundOrderLineId(), 901L, first.getPackageId(), first.getStockTransactionId(), "600"),
                postedLine(third.getOutboundOrderLineId(), 901L, third.getPackageId(), third.getStockTransactionId(), "800")));
        return task;
    }

    private void verifyNoWrites() {
        verify(taskMapper, never()).updateById(any(RdLoadingTaskDO.class));
        verify(taskLineMapper, never()).updateById(any(RdLoadingTaskLineDO.class));
        verify(scanRecordMapper, never()).insert(any(RdLoadingScanRecordDO.class));
        verify(outboundOrderMapper, never()).insert(any(RdOutboundOrderDO.class));
        verify(outboundOrderLineMapper, never()).insert(any(RdOutboundOrderLineDO.class));
        verify(taskHistoryService, never()).append(any());
    }

    private static Set<String> invalidProperties(Object req) {
        return validator.validate(req).stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    private static void assertCode(int code, org.junit.jupiter.api.function.Executable operation) {
        assertEquals(code, assertThrows(ServiceException.class, operation).getCode());
    }

    static RdLoadingBatchScanConfirmReqVO batchRequest() {
        var req = new RdLoadingBatchScanConfirmReqVO();
        req.setTaskId(500L);
        setBatchVersion(req, 0);
        req.setRepresentativePackageCode("PK600");
        req.setIdempotencyKey("batch-one");
        req.setTaskLineIds(List.of(600L, 601L));
        return req;
    }

    static void setBatchVersion(RdLoadingBatchScanConfirmReqVO req, Integer version) {
        req.setVersion(version);
    }

    static RdLoadingConfirmOutboundReqVO outboundRequest(Integer version) {
        var req = new RdLoadingConfirmOutboundReqVO();
        req.setTaskId(500L);
        req.setVersion(version);
        return req;
    }

    static RdLoadingTaskDO task(String status, int version) {
        var task = new RdLoadingTaskDO();
        task.setId(500L);
        task.setTaskNo("LD-500");
        task.setPickupSlipId(50L);
        task.setStatus(status);
        task.setPlannedPackageCount(2);
        task.setScannedPackageCount(0);
        task.setScannedWeight(BigDecimal.ZERO);
        task.setPlanVersion(1);
        task.setVersion(version);
        return task;
    }

    static RdLoadingTaskLineDO line(long id, String code, String status, String weight) {
        var line = new RdLoadingTaskLineDO();
        line.setId(id);
        line.setTaskId(500L);
        line.setLineNo((int) id);
        line.setPackageId(id + 1000);
        line.setPackageCode(code);
        line.setLineStatus(status);
        line.setPackageWeight(new BigDecimal(weight));
        line.setVersion(0);
        return line;
    }

    private static RdLoadingTaskLineDO completedLine(long id, long orderId, String weight) {
        var line = line(id, "PK" + id, "OUTBOUNDED", weight);
        line.setOutboundOrderId(orderId);
        line.setOutboundOrderLineId(id + 1000);
        line.setStockTransactionId(id + 2000);
        return line;
    }

    private static RdOutboundOrderDO postedOrder(long id) {
        return new RdOutboundOrderDO().setId(id).setStatus("POSTED");
    }

    private static RdOutboundOrderLineDO postedLine(long id, long orderId, long packageId, long transactionId, String weight) {
        var line = new RdOutboundOrderLineDO();
        line.setId(id);
        line.setOrderId(orderId);
        line.setPackageId(packageId);
        line.setPackageWeight(new BigDecimal(weight));
        line.setStockTransactionId(transactionId);
        return line;
    }
}
