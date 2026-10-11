package com.ruiding.erp.service.batchscan;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import com.ruiding.erp.controller.admin.common.vo.RdBatchScanStatusRespVO;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingScanRecordDO;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingTaskDO;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayBatchScanRecordDO;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayTaskDO;
import com.ruiding.erp.service.loading.RdLoadingDispatchService;
import com.ruiding.erp.service.putaway.RdPutawayTaskService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static com.ruiding.erp.enums.ErrorCodeConstants.*;
import static com.ruiding.erp.service.batchscan.RdBatchScanStatusTest.digest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RdBatchScanStatusTransactionTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void shouldUseActiveTransactionWithoutChangingAnyRowsOrDomainState(boolean loading) throws Exception {
        try (var f = new Fixture(loading)) {
            for (String state : List.of("UNKNOWN", "NOT_APPLIED_STALE", "APPLIED", "legacy")) {
                if (state.equals("NOT_APPLIED_STALE")) f.jdbc.update("UPDATE task_header SET version=5");
                if (state.equals("APPLIED")) f.jdbc.update("INSERT INTO request_record VALUES (91,?,20)", digest());
                if (state.equals("legacy")) f.jdbc.update("UPDATE request_record SET request_digest=NULL");
                var before = f.snapshot();
                f.mock.clearReads();
                if (state.equals("legacy")) f.mock.assertConflict(f::status);
                else assertEquals(state, f.status().getState().name());
                assertEquals(before, f.snapshot());
                f.mock.verifyReadOnly();
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            }
        }
    }

    static Stream<Arguments> pendingOutcomes() {
        return Stream.of(false, true).flatMap(loading -> Stream.of("applied", "stale", "rollback")
                .map(outcome -> Arguments.of(loading, outcome)));
    }

    @ParameterizedTest @MethodSource("pendingOutcomes")
    void shouldWaitForTaskLockThenObserveCommitOrRollback(boolean loading, String outcome) throws Exception {
        try (var f = new Fixture(loading); var writer = f.dataSource.getConnection()) {
            writer.setAutoCommit(false);
            try (var statement = writer.createStatement(); var rows = statement.executeQuery("SELECT * FROM task_header WHERE id=500 FOR UPDATE")) {
                assertTrue(rows.next());
            }
            try (var statement = writer.createStatement()) {
                statement.executeUpdate("UPDATE task_header SET version=5,status='COMPLETED'");
            }
            if (!outcome.equals("stale")) {
                try (var insert = writer.prepareStatement("INSERT INTO request_record VALUES (91,?,20)")) {
                    insert.setString(1, digest());
                    insert.executeUpdate();
                }
            }
            var executor = Executors.newSingleThreadExecutor();
            try {
                var result = executor.submit(f::status);
                assertTrue(f.lockAttempted.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> result.get(200, TimeUnit.MILLISECONDS));
                if (outcome.equals("rollback")) writer.rollback();
                else writer.commit();
                var before = f.snapshot();
                var response = result.get(5, TimeUnit.SECONDS);
                assertEquals(outcome.equals("applied") ? "APPLIED" : outcome.equals("stale") ? "NOT_APPLIED_STALE" : "UNKNOWN",
                        response.getState().name());
                assertEquals(outcome.equals("rollback") ? 4 : 5, response.getCurrentVersion());
                assertEquals(outcome.equals("applied") ? 91L : null, response.getRecordId());
                assertEquals(before, f.snapshot());
                f.mock.verifyReadOnly();
            } finally {
                writer.rollback();
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void shouldHoldSameTaskLockUntilStatusReturnsAndSerializeExistingConfirm(boolean loading) throws Exception {
        try (var f = new Fixture(loading)) {
            f.jdbc.update("UPDATE task_header SET version=5");
            var statusHasLock = new CountDownLatch(1);
            var releaseStatus = new CountDownLatch(1);
            var firstRead = new AtomicBoolean(true);
            f.beforeRecordRead = () -> {
                if (firstRead.compareAndSet(true, false)) {
                    statusHasLock.countDown();
                    try { assertTrue(releaseStatus.await(5, TimeUnit.SECONDS)); }
                    catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new AssertionError(ex); }
                }
            };
            var executor = Executors.newFixedThreadPool(2);
            try {
                var status = executor.submit(f::status);
                assertTrue(statusHasLock.await(5, TimeUnit.SECONDS));
                f.lockAttempted = new CountDownLatch(1);
                var confirmation = executor.submit(() -> assertThrows(ServiceException.class, f::confirm));
                assertTrue(f.lockAttempted.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> confirmation.get(200, TimeUnit.MILLISECONDS));
                var before = f.snapshot();
                releaseStatus.countDown();
                assertEquals("NOT_APPLIED_STALE", status.get(5, TimeUnit.SECONDS).getState().name());
                assertEquals((loading ? LOADING_TASK_VERSION_CONFLICT : PUTAWAY_TASK_VERSION_CONFLICT).getCode(),
                        confirmation.get(5, TimeUnit.SECONDS).getCode());
                assertEquals(before, f.snapshot());
                f.mock.verifyNoDomainReadsOrWrites();
                if (loading) {
                    verify(f.mock.loadingTasks, times(2)).selectByIdForUpdate(500L);
                    verify(f.mock.loadingRecords, times(2)).selectByTaskAndIdempotencyKey(500L, "request-one");
                } else {
                    verify(f.mock.putawayTasks, times(2)).selectByIdForUpdate(500L);
                    verify(f.mock.putawayRecords, times(2)).selectByTaskAndKey(500L, "request-one");
                }
                verifyNoMoreInteractions(f.mock.tasks(), f.mock.records());
            } finally {
                releaseStatus.countDown();
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
    }

    private static class Fixture implements AutoCloseable {
        final RdBatchScanStatusTest.Fixture mock;
        final DriverManagerDataSource dataSource;
        final Connection keepAlive;
        final JdbcTemplate jdbc;
        final Object service;
        volatile CountDownLatch lockAttempted = new CountDownLatch(1);
        volatile Runnable beforeRecordRead = () -> {};

        Fixture(boolean loading) throws SQLException {
            mock = new RdBatchScanStatusTest.Fixture(loading);
            dataSource = new DriverManagerDataSource("jdbc:h2:mem:d4_status_" + UUID.randomUUID()
                    + ";MODE=MySQL;LOCK_TIMEOUT=10000", "sa", "");
            keepAlive = dataSource.getConnection();
            jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("CREATE TABLE task_header (id BIGINT PRIMARY KEY,version INT,status VARCHAR(32))");
            jdbc.execute("CREATE TABLE request_record (id BIGINT PRIMARY KEY,request_digest CHAR(64),operator_id BIGINT)");
            jdbc.update("INSERT INTO task_header VALUES (500,4,'ASSIGNED')");
            if (loading) {
                when(mock.loadingTasks.selectByIdForUpdate(500L)).thenAnswer(inv -> readLoadingTask());
                when(mock.loadingRecords.selectByTaskAndIdempotencyKey(500L, "request-one")).thenAnswer(inv -> {
                    checkTransaction(); beforeRecordRead.run();
                    var records = jdbc.query("SELECT * FROM request_record", (rs, row) -> {
                        var record = new RdLoadingScanRecordDO();
                        record.setId(rs.getLong("id")); record.setRequestDigest(rs.getString("request_digest"));
                        record.setOperatorId(rs.getLong("operator_id"));
                        return record;
                    });
                    return records.isEmpty() ? null : records.get(0);
                });
            } else {
                when(mock.putawayTasks.selectByIdForUpdate(500L)).thenAnswer(inv -> readPutawayTask());
                when(mock.putawayRecords.selectByTaskAndKey(500L, "request-one")).thenAnswer(inv -> {
                    checkTransaction(); beforeRecordRead.run();
                    var records = jdbc.query("SELECT * FROM request_record", (rs, row) ->
                            new RdPutawayBatchScanRecordDO().setId(rs.getLong("id"))
                                    .setRequestDigest(rs.getString("request_digest")).setOperatorId(rs.getLong("operator_id")));
                    return records.isEmpty() ? null : records.get(0);
                });
            }
            var proxy = new ProxyFactory(mock.service);
            proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource), new AnnotationTransactionAttributeSource()));
            service = proxy.getProxy();
        }

        private RdLoadingTaskDO readLoadingTask() {
            checkTransaction(); lockAttempted.countDown();
            return jdbc.queryForObject("SELECT * FROM task_header WHERE id=500 FOR UPDATE", (rs, row) -> {
                var task = new RdLoadingTaskDO();
                task.setId(500L); task.setVersion(rs.getInt("version")); task.setStatus(rs.getString("status"));
                return task;
            });
        }
        private RdPutawayTaskDO readPutawayTask() {
            checkTransaction(); lockAttempted.countDown();
            return jdbc.queryForObject("SELECT * FROM task_header WHERE id=500 FOR UPDATE", (rs, row) ->
                    new RdPutawayTaskDO().setId(500L).setVersion(rs.getInt("version")).setStatus(rs.getString("status")));
        }
        private void checkTransaction() {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly(), "FOR UPDATE requires a normal locking transaction");
        }
        RdBatchScanStatusRespVO status() {
            return mock.loading ? ((RdLoadingDispatchService) service).getBatchScanStatus(mock.loadingRequest, 20L)
                    : ((RdPutawayTaskService) service).getBatchScanStatus(mock.putawayRequest, 20L);
        }
        void confirm() {
            if (mock.loading) ((RdLoadingDispatchService) service).confirmBatchScan(mock.loadingRequest, 20L, "warehouse");
            else ((RdPutawayTaskService) service).confirmBatchScan(mock.putawayRequest, 20L, "warehouse");
        }
        List<List<Map<String, Object>>> snapshot() {
            return List.of(jdbc.queryForList("SELECT * FROM task_header ORDER BY id"),
                    jdbc.queryForList("SELECT * FROM request_record ORDER BY id"));
        }
        @Override public void close() throws SQLException { keepAlive.close(); }
    }
}
