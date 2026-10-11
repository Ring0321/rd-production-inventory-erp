package com.ruiding.erp.service.batchscan;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import com.mzt.logapi.starter.annotation.LogRecord;
import com.ruiding.erp.controller.admin.common.vo.RdBatchScanStatusRespVO;
import com.ruiding.erp.controller.admin.loading.RdLoadingDispatchController;
import com.ruiding.erp.controller.admin.loading.vo.RdLoadingBatchScanConfirmReqVO;
import com.ruiding.erp.controller.admin.putaway.RdPutawayTaskController;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayBatchScanConfirmReqVO;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingScanRecordDO;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingTaskDO;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayBatchScanRecordDO;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayTaskDO;
import com.ruiding.erp.dal.mysql.loading.RdLoadingScanRecordMapper;
import com.ruiding.erp.dal.mysql.loading.RdLoadingTaskMapper;
import com.ruiding.erp.dal.mysql.putaway.RdPutawayBatchScanRecordMapper;
import com.ruiding.erp.dal.mysql.putaway.RdPutawayTaskMapper;
import com.ruiding.erp.service.loading.RdLoadingDispatchService;
import com.ruiding.erp.service.loading.RdLoadingDispatchServiceImpl;
import com.ruiding.erp.service.putaway.RdPutawayTaskService;
import com.ruiding.erp.service.putaway.RdPutawayTaskServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import javax.validation.Valid;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static com.ruiding.erp.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RdBatchScanStatusTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void shouldLeaveAbsentSameVersionUnknownEvenAtTerminalStatus(boolean loading) {
        for (String status : List.of("ASSIGNED", "COMPLETED", "CANCELLED")) {
            var f = new Fixture(loading);
            f.current(4, status);
            f.assertResponse(f.status(20L), "UNKNOWN", 4, status, null);
            f.verifyReadOnly();
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void shouldProveAbsentOldVersionCannotPassConfirmation(boolean loading) {
        var f = new Fixture(loading);
        f.current(5, "COMPLETED");
        f.assertResponse(f.status(20L), "NOT_APPLIED_STALE", 5, "COMPLETED", null);
        f.verifyReadOnly();
        f.clearReads();
        assertEquals((loading ? LOADING_TASK_VERSION_CONFLICT : PUTAWAY_TASK_VERSION_CONFLICT).getCode(),
                assertThrows(ServiceException.class, () -> f.confirm(20L)).getCode());
        f.verifyReadOnly();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void shouldReturnAppliedBeforeChangedCurrentVersionOrStatus(boolean loading) {
        var f = new Fixture(loading);
        f.remember(digest(), 20L);
        f.current(9, "EXCEPTION_PENDING");
        f.ids(List.of(601L, 600L));
        f.code(" https://id.tjldsic.com/pkg/PK600?source=pda#scan ");
        f.device("another-device");
        f.assertResponse(f.status(20L), "APPLIED", 9, "EXCEPTION_PENDING", 91L);
        f.verifyReadOnly();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void shouldPreserveModuleSpecificCaseNormalization(boolean loading) {
        var f = new Fixture(loading);
        f.remember(digest(), 20L);
        f.code("pk600");
        if (loading) {
            f.assertConflict(() -> f.status(20L));
        } else {
            f.assertResponse(f.status(20L), "APPLIED", 4, "ASSIGNED", 91L);
        }
        f.verifyReadOnly();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void shouldRejectActorContentAndLegacyConflictsExactlyLikeReplay(boolean loading) {
        for (String change : List.of("actor", "selection", "representative", "version", "legacy", "digest")) {
            var f = new Fixture(loading);
            f.remember(change.equals("legacy") ? null : change.equals("digest") ? "unverifiable" : digest(), 20L);
            f.current(9, "COMPLETED");
            if (change.equals("selection")) f.ids(List.of(600L));
            if (change.equals("representative")) f.code("PK601");
            if (change.equals("version")) f.version(5);
            long actor = change.equals("actor") ? 21L : 20L;
            f.assertConflict(() -> f.status(actor));
            f.verifyReadOnly();
            f.clearReads();
            f.assertConflict(() -> f.confirm(actor));
            f.verifyReadOnly();
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void shouldRejectMalformedSelectionBeforeTaskRead(boolean loading) {
        for (List<Long> ids : Arrays.asList(List.<Long>of(), List.of(600L, 600L), List.of(0L),
                Arrays.asList(600L, null))) {
            var f = new Fixture(loading);
            f.ids(ids);
            assertEquals((loading ? LOADING_PACKAGE_INVALID : PUTAWAY_BATCH_SELECTION_INVALID).getCode(),
                    assertThrows(ServiceException.class, () -> f.status(20L)).getCode());
            verifyNoInteractions(f.tasks(), f.records());
            f.verifyNoDomainReadsOrWrites();
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void shouldRejectMissingVersionBeforeTaskRead(boolean loading) {
        var f = new Fixture(loading);
        f.version(null);
        assertEquals((loading ? LOADING_TASK_VERSION_CONFLICT : PUTAWAY_BATCH_SELECTION_INVALID).getCode(),
                assertThrows(ServiceException.class, () -> f.status(20L)).getCode());
        verifyNoInteractions(f.tasks(), f.records());
        f.verifyNoDomainReadsOrWrites();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void shouldRejectMissingTaskWithoutReadingRecord(boolean loading) {
        var f = new Fixture(loading);
        if (loading) when(f.loadingTasks.selectByIdForUpdate(500L)).thenReturn(null);
        else when(f.putawayTasks.selectByIdForUpdate(500L)).thenReturn(null);
        assertEquals((loading ? LOADING_TASK_NOT_EXISTS : PUTAWAY_TASK_NOT_EXISTS).getCode(),
                assertThrows(ServiceException.class, () -> f.status(20L)).getCode());
        verifyNoInteractions(f.records());
        f.verifyNoDomainReadsOrWrites();
    }

    @Test void shouldKeepPutawayPermissionGuardBeforeReads() {
        var f = new Fixture(false);
        when(f.permissions.hasAnyPermissions(20L, "rd:putaway-task:confirm")).thenReturn(false);
        assertEquals(FORBIDDEN.getCode(), assertThrows(ServiceException.class, () -> f.status(20L)).getCode());
        verifyNoInteractions(f.tasks(), f.records());
        f.verifyNoDomainReadsOrWrites();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void shouldExposePostWithExactConfirmVoAndExistingPermission(boolean loading) throws Exception {
        Class<?> controller = loading ? RdLoadingDispatchController.class : RdPutawayTaskController.class;
        Class<?> request = loading ? RdLoadingBatchScanConfirmReqVO.class : RdPutawayBatchScanConfirmReqVO.class;
        Method status = controller.getMethod("getBatchScanStatus", request);
        Method confirm = controller.getMethod("confirmBatchScan", request);
        assertArrayEquals(new String[]{"/batch-scan/status"}, status.getAnnotation(PostMapping.class).value());
        assertEquals(confirm.getAnnotation(PreAuthorize.class).value(), status.getAnnotation(PreAuthorize.class).value());
        assertNotNull(status.getParameters()[0].getAnnotation(Valid.class));
        assertNotNull(status.getParameters()[0].getAnnotation(RequestBody.class));
        assertEquals(RdBatchScanStatusRespVO.class,
                ((ParameterizedType) status.getGenericReturnType()).getActualTypeArguments()[0]);
        Method service = (loading ? RdLoadingDispatchServiceImpl.class : RdPutawayTaskServiceImpl.class)
                .getMethod("getBatchScanStatus", request, Long.class);
        assertNotNull(service.getAnnotation(Transactional.class));
        assertNull(service.getAnnotation(LogRecord.class));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void shouldSerializeOnlySharedContractAndOmitAbsentRecordId(boolean loading) {
        var f = new Fixture(loading);
        Map<String, Object> unknown = JsonUtils.parseMap(JsonUtils.toJsonString(f.status(20L)));
        assertEquals(Set.of("taskId", "requestVersion", "currentVersion", "taskStatus", "state", "message"), unknown.keySet());
        assertEquals("UNKNOWN", unknown.get("state"));
        f.remember(digest(), 20L);
        Map<String, Object> applied = JsonUtils.parseMap(JsonUtils.toJsonString(f.status(20L)));
        assertEquals(Set.of("taskId", "requestVersion", "currentVersion", "taskStatus", "state", "recordId", "message"), applied.keySet());
        assertEquals("APPLIED", applied.get("state"));
        assertEquals(91L, ((Number) applied.get("recordId")).longValue());
        assertArrayEquals(new String[]{"APPLIED", "NOT_APPLIED_STALE", "UNKNOWN"},
                Arrays.stream(RdBatchScanStatusRespVO.State.values()).map(Enum::name).toArray(String[]::new));
    }

    static String digest() {
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(new TreeMap<>(Map.of(
                "taskId", 500L, "version", 4, "packageCode", "PK600",
                "lineIds", List.of(600L, 601L), "operatorId", 20L))));
    }

    static class Fixture {
        final boolean loading;
        final RdPutawayTaskMapper putawayTasks = mock(RdPutawayTaskMapper.class);
        final RdPutawayBatchScanRecordMapper putawayRecords = mock(RdPutawayBatchScanRecordMapper.class);
        final RdLoadingTaskMapper loadingTasks = mock(RdLoadingTaskMapper.class);
        final RdLoadingScanRecordMapper loadingRecords = mock(RdLoadingScanRecordMapper.class);
        final PermissionService permissions = mock(PermissionService.class);
        final RdPutawayTaskDO putawayTask = new RdPutawayTaskDO().setId(500L).setVersion(4).setStatus("ASSIGNED");
        final RdLoadingTaskDO loadingTask = new RdLoadingTaskDO();
        final RdPutawayBatchScanConfirmReqVO putawayRequest = new RdPutawayBatchScanConfirmReqVO();
        final RdLoadingBatchScanConfirmReqVO loadingRequest = new RdLoadingBatchScanConfirmReqVO();
        final Object service;
        final List<Object> domain = new ArrayList<>();

        Fixture(boolean loading) {
            this.loading = loading;
            loadingTask.setId(500L); loadingTask.setVersion(4); loadingTask.setStatus("ASSIGNED");
            putawayRequest.setId(500L); putawayRequest.setIdempotencyKey("request-one");
            loadingRequest.setTaskId(500L); loadingRequest.setIdempotencyKey("request-one");
            version(4); ids(List.of(600L, 601L)); code("PK600");
            service = loading ? new RdLoadingDispatchServiceImpl() : new RdPutawayTaskServiceImpl();
            ReflectionTestUtils.setField(service, "taskMapper", tasks());
            ReflectionTestUtils.setField(service, loading ? "scanRecordMapper" : "batchScanRecordMapper", records());
            if (loading) when(loadingTasks.selectByIdForUpdate(500L)).thenReturn(loadingTask);
            else {
                when(putawayTasks.selectByIdForUpdate(500L)).thenReturn(putawayTask);
                ReflectionTestUtils.setField(service, "permissionService", permissions);
                when(permissions.hasAnyPermissions(anyLong(), eq("rd:putaway-task:confirm"))).thenReturn(true);
            }
            // Every collaborator other than the task and request record must remain untouched.
            for (var field : service.getClass().getDeclaredFields()) {
                if (field.isAnnotationPresent(javax.annotation.Resource.class)
                        && !field.getName().equals("taskMapper") && !field.getName().equals("permissionService")
                        && !field.getName().equals(loading ? "scanRecordMapper" : "batchScanRecordMapper")) {
                    Object collaborator = mock(field.getType());
                    ReflectionTestUtils.setField(service, field.getName(), collaborator);
                    domain.add(collaborator);
                }
            }
        }

        Object tasks() { return loading ? loadingTasks : putawayTasks; }
        Object records() { return loading ? loadingRecords : putawayRecords; }
        RdBatchScanStatusRespVO status(Long actor) {
            return loading ? ((RdLoadingDispatchService) service).getBatchScanStatus(loadingRequest, actor)
                    : ((RdPutawayTaskService) service).getBatchScanStatus(putawayRequest, actor);
        }
        void confirm(Long actor) {
            if (loading) ((RdLoadingDispatchService) service).confirmBatchScan(loadingRequest, actor, "warehouse");
            else ((RdPutawayTaskService) service).confirmBatchScan(putawayRequest, actor, "warehouse");
        }
        void current(int version, String status) {
            putawayTask.setVersion(version).setStatus(status);
            loadingTask.setVersion(version); loadingTask.setStatus(status);
        }
        void version(Integer version) { putawayRequest.setVersion(version); loadingRequest.setVersion(version); }
        void ids(List<Long> ids) { putawayRequest.setTaskLineIds(ids); loadingRequest.setTaskLineIds(ids); }
        void code(String code) { putawayRequest.setPackageCode(code); loadingRequest.setRepresentativePackageCode(code); }
        void device(String device) { putawayRequest.setDeviceCode(device); loadingRequest.setDeviceCode(device); }
        void remember(String digest, Long actor) {
            if (loading) {
                var record = new RdLoadingScanRecordDO();
                record.setId(91L); record.setOperatorId(actor); record.setRequestDigest(digest);
                when(loadingRecords.selectByTaskAndIdempotencyKey(500L, "request-one")).thenReturn(record);
            } else {
                var record = new RdPutawayBatchScanRecordDO().setId(91L).setOperatorId(actor).setRequestDigest(digest);
                when(putawayRecords.selectByTaskAndKey(500L, "request-one")).thenReturn(record);
            }
        }
        void assertConflict(Runnable action) {
            assertEquals((loading ? LOADING_SCAN_KEY_CONFLICT : PUTAWAY_SCAN_KEY_CONFLICT).getCode(),
                    assertThrows(ServiceException.class, action::run).getCode());
        }
        void assertResponse(RdBatchScanStatusRespVO response, String state, int version, String taskStatus, Long recordId) {
            assertEquals(500L, response.getTaskId());
            assertEquals(4, response.getRequestVersion());
            assertEquals(version, response.getCurrentVersion());
            assertEquals(taskStatus, response.getTaskStatus());
            assertEquals(state, response.getState().name());
            assertEquals(recordId, response.getRecordId());
            assertFalse(response.getMessage().isBlank());
        }
        void clearReads() { clearInvocations(tasks(), records(), permissions); }
        void verifyNoDomainReadsOrWrites() { verifyNoInteractions(domain.toArray()); }
        void verifyReadOnly() {
            var order = inOrder(tasks(), records());
            if (loading) {
                order.verify(loadingTasks).selectByIdForUpdate(500L);
                order.verify(loadingRecords).selectByTaskAndIdempotencyKey(500L, "request-one");
            } else {
                order.verify(putawayTasks).selectByIdForUpdate(500L);
                order.verify(putawayRecords).selectByTaskAndKey(500L, "request-one");
            }
            verifyNoMoreInteractions(tasks(), records());
            verifyNoDomainReadsOrWrites();
        }
    }
}
