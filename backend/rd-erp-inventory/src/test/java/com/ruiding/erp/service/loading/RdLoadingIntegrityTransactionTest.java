package com.ruiding.erp.service.loading;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.ruiding.erp.dal.dataobject.loading.*;
import com.ruiding.erp.dal.dataobject.order.RdOrderFulfillmentDO;
import com.ruiding.erp.dal.dataobject.outbound.RdOutboundOrderDO;
import com.ruiding.erp.dal.dataobject.outbound.RdOutboundOrderLineDO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockLotDO;
import com.ruiding.erp.dal.mysql.loading.*;
import com.ruiding.erp.dal.mysql.masterdata.RdWarehouseLocationMapper;
import com.ruiding.erp.dal.mysql.order.RdOrderFulfillmentMapper;
import com.ruiding.erp.dal.mysql.outbound.RdOutboundOrderLineMapper;
import com.ruiding.erp.dal.mysql.outbound.RdOutboundOrderMapper;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.dal.mysql.stock.RdStockLotMapper;
import com.ruiding.erp.service.outbound.RdOutboundOrderService;
import com.ruiding.erp.service.packageunit.RdPackageTaskHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static com.ruiding.erp.enums.ErrorCodeConstants.*;
import static com.ruiding.erp.service.loading.RdLoadingIntegrityTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RdLoadingIntegrityTransactionTest {
    @Test void shouldPersistDigestWithConfirmationAndReplayFromStoredRecord() throws Exception {
        try (var f = new Fixture()) {
            var first = f.service.confirmBatchScan(batchRequest(), 20L, "warehouse");
            assertEquals(2, first.getConfirmedCount());
            String digest = f.jdbc.queryForObject("SELECT request_digest FROM scan_record", String.class);
            assertNotNull(digest);
            assertTrue(digest.matches("[0-9a-f]{64}"));
            assertEquals(DigestUtil.sha256Hex(JsonUtils.toJsonString(new TreeMap<>(Map.of(
                    "taskId", 500L, "version", 0, "operatorId", 20L,
                    "packageCode", "PK600", "lineIds", List.of(600L, 601L))))), digest);
            assertEquals(2, f.count("history"));
            var before = f.snapshot();
            f.clearWrites();

            var replay = f.service.confirmBatchScan(batchRequest(), 20L, "warehouse");

            assertTrue(replay.getReplayed());
            assertEquals(first.getRecordId(), replay.getRecordId());
            assertEquals(0, replay.getConfirmedCount());
            assertEquals(before, f.snapshot());
            f.verifyNoWrites();
        }
    }

    @Test void shouldRollbackTaskLinesDigestAndHistoryOnLateHistoryFailure() throws Exception {
        try (var f = new Fixture()) {
            doAnswer(inv -> {
                f.jdbc.update("INSERT INTO history (source_id) VALUES (88)");
                throw new DataIntegrityViolationException("late loading history failure");
            }).when(f.history).append(any());

            assertThrows(DataIntegrityViolationException.class,
                    () -> f.service.confirmBatchScan(batchRequest(), 20L, "warehouse"));

            f.assertBatchUnchanged();
        }
    }

    @Test void shouldRollbackEarlierLineWhenLaterSelectedLineIsInvalid() throws Exception {
        try (var f = new Fixture()) {
            f.jdbc.update("UPDATE loading_line SET status='EXCEPTION' WHERE id=601");
            var before = f.snapshot();

            assertEquals(LOADING_TASK_STATUS_INVALID.getCode(), assertThrows(ServiceException.class,
                    () -> f.service.confirmBatchScan(batchRequest(), 20L, "warehouse")).getCode());

            assertEquals(before, f.snapshot());
            assertEquals(0, f.count("scan_record"));
            assertEquals(0, f.count("history"));
        }
    }

    @Test void shouldRollbackTaskAndLinesWhenDigestRecordInsertFails() throws Exception {
        try (var f = new Fixture()) {
            doAnswer(inv -> {
                f.jdbc.update("INSERT INTO scan_record (id,request_digest) VALUES (88,'failed-write')");
                throw new DataIntegrityViolationException("loading scan record failure");
            }).when(f.scans).insert(any(RdLoadingScanRecordDO.class));

            assertThrows(DataIntegrityViolationException.class,
                    () -> f.service.confirmBatchScan(batchRequest(), 20L, "warehouse"));

            f.assertBatchUnchanged();
        }
    }

    @Test void shouldRollbackOutboundOrdersPackagesRelationsAndLedgerOnPostingFailure() throws Exception {
        try (var f = new Fixture()) {
            f.wireOutbound();
            doAnswer(inv -> {
                f.jdbc.update("INSERT INTO stock_ledger VALUES (2600),(2601)");
                f.jdbc.update("UPDATE package_unit SET status='OUTBOUNDED'");
                throw new DataIntegrityViolationException("late outbound posting failure");
            }).when(f.outboundService).postOutboundOrder(eq(900L), isNull(), eq(20L), eq("warehouse"));

            assertThrows(DataIntegrityViolationException.class,
                    () -> f.service.confirmOutbound(outboundRequest(0), 20L, "warehouse"));

            f.assertOutboundUnchanged();
        }
    }

    @Test void shouldRollbackCompletedOutboundAndClaimReleaseIfPickupSlipIsMissing() throws Exception {
        try (var f = new Fixture()) {
            f.wireOutbound();
            when(f.slips.selectByIdForUpdate(50L, 1L)).thenReturn(null);

            assertEquals(PICKUP_SLIP_NOT_EXISTS.getCode(), assertThrows(ServiceException.class,
                    () -> f.service.confirmOutbound(outboundRequest(0), 20L, "warehouse")).getCode());

            f.assertOutboundUnchanged();
        }
    }

    @Test void shouldRecoverCompletedResponseAfterFreshReadWithoutReposting() throws Exception {
        try (var f = new Fixture()) {
            f.wireOutbound();
            var first = f.service.confirmOutbound(outboundRequest(0), 20L, "warehouse");
            assertEquals(2, first.getOutboundPackageCount());
            assertEquals(new BigDecimal("2000"), first.getOutboundWeight());
            var before = f.snapshot();
            f.clearWrites();

            var replay = f.service.confirmOutbound(outboundRequest(0), 20L, "warehouse");

            assertEquals(first, replay);
            assertEquals(before, f.snapshot());
            assertEquals(2, f.count("stock_ledger"));
            assertEquals(1, f.count("outbound_order"));
            assertEquals(2, f.count("outbound_line"));
            f.verifyNoWrites();
            verifyNoInteractions(f.outboundService);
        }
    }

    private static class Fixture implements AutoCloseable {
        final RdLoadingTaskMapper tasks = mock(RdLoadingTaskMapper.class);
        final RdLoadingTaskLineMapper lines = mock(RdLoadingTaskLineMapper.class);
        final RdLoadingScanRecordMapper scans = mock(RdLoadingScanRecordMapper.class);
        final RdPackageTaskHistoryService history = mock(RdPackageTaskHistoryService.class);
        final RdLoadingExceptionMapper exceptions = mock(RdLoadingExceptionMapper.class);
        final RdLoadingPackageClaimMapper claims = mock(RdLoadingPackageClaimMapper.class);
        final RdPickupSlipMapper slips = mock(RdPickupSlipMapper.class);
        final RdPackageUnitMapper packages = mock(RdPackageUnitMapper.class);
        final RdStockLotMapper stockLots = mock(RdStockLotMapper.class);
        final RdWarehouseLocationMapper locations = mock(RdWarehouseLocationMapper.class);
        final RdOrderFulfillmentMapper fulfillments = mock(RdOrderFulfillmentMapper.class);
        final RdOutboundOrderMapper outboundOrders = mock(RdOutboundOrderMapper.class);
        final RdOutboundOrderLineMapper outboundLines = mock(RdOutboundOrderLineMapper.class);
        final RdOutboundOrderService outboundService = mock(RdOutboundOrderService.class);
        final JdbcTemplate jdbc;
        final Connection keepAlive;
        final RdLoadingDispatchService service;

        Fixture() throws SQLException {
            TenantContextHolder.setTenantId(1L);
            var dataSource = new DriverManagerDataSource("jdbc:h2:mem:d4_" + UUID.randomUUID() + ";MODE=MySQL", "sa", "");
            keepAlive = dataSource.getConnection();
            jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("CREATE TABLE loading_task (id BIGINT PRIMARY KEY,status VARCHAR(32),version INT,scanned_count INT,scanned_weight DECIMAL(24,6),confirm_time TIMESTAMP,scan_complete_time TIMESTAMP)");
            jdbc.execute("CREATE TABLE loading_line (id BIGINT PRIMARY KEY,status VARCHAR(32),version INT,outbound_id BIGINT,outbound_line_id BIGINT,transaction_id BIGINT)");
            jdbc.execute("CREATE TABLE scan_record (id BIGINT PRIMARY KEY,request_key VARCHAR(128),request_digest CHAR(64),operator_id BIGINT,package_code VARCHAR(32),scan_time TIMESTAMP)");
            jdbc.execute("CREATE TABLE history (source_id BIGINT)");
            jdbc.execute("CREATE TABLE outbound_order (id BIGINT PRIMARY KEY,status VARCHAR(32))");
            jdbc.execute("CREATE TABLE outbound_line (id BIGINT PRIMARY KEY,order_id BIGINT,package_id BIGINT,transaction_id BIGINT)");
            jdbc.execute("CREATE TABLE stock_ledger (id BIGINT PRIMARY KEY)");
            jdbc.execute("CREATE TABLE package_unit (id BIGINT PRIMARY KEY,status VARCHAR(32))");
            jdbc.execute("CREATE TABLE package_claim (id BIGINT PRIMARY KEY)");
            jdbc.execute("CREATE TABLE pickup_slip (id BIGINT PRIMARY KEY,status VARCHAR(32))");
            jdbc.update("INSERT INTO loading_task (id,status,version,scanned_count,scanned_weight) VALUES (500,'ASSIGNED',0,0,0)");
            jdbc.update("INSERT INTO loading_line (id,status,version) VALUES (600,'PLANNED',0),(601,'PLANNED',0)");
            jdbc.update("INSERT INTO package_unit VALUES (1600,'IN_STOCK'),(1601,'IN_STOCK')");
            jdbc.update("INSERT INTO package_claim VALUES (600),(601)");
            jdbc.update("INSERT INTO pickup_slip VALUES (50,'DISPATCHED')");
            var target = new RdLoadingDispatchServiceImpl();
            Map.ofEntries(Map.entry("taskMapper", tasks), Map.entry("taskLineMapper", lines),
                    Map.entry("scanRecordMapper", scans), Map.entry("taskHistoryService", history),
                    Map.entry("exceptionMapper", exceptions), Map.entry("claimMapper", claims), Map.entry("slipMapper", slips),
                    Map.entry("packageMapper", packages), Map.entry("stockLotMapper", stockLots), Map.entry("locationMapper", locations),
                    Map.entry("fulfillmentMapper", fulfillments), Map.entry("outboundOrderMapper", outboundOrders),
                    Map.entry("outboundOrderLineMapper", outboundLines), Map.entry("outboundOrderService", outboundService))
                    .forEach((field, value) -> ReflectionTestUtils.setField(target, field, value));
            var proxy = new ProxyFactory(target);
            proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource), new AnnotationTransactionAttributeSource()));
            service = (RdLoadingDispatchService) proxy.getProxy();
            wireBatch();
        }

        private void wireBatch() {
            when(tasks.selectByIdForUpdate(500L)).thenAnswer(inv -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                return jdbc.queryForObject("SELECT * FROM loading_task WHERE id=500 FOR UPDATE", (rs, n) -> {
                    var task = task(rs.getString("status"), rs.getInt("version"));
                    task.setTenantId(1L);
                    task.setScannedPackageCount(rs.getInt("scanned_count"));
                    task.setScannedWeight(rs.getBigDecimal("scanned_weight"));
                    var confirmed = rs.getTimestamp("confirm_time");
                    if (confirmed != null) task.setConfirmTime(confirmed.toLocalDateTime());
                    return task;
                });
            });
            when(lines.selectListByTaskIdForUpdate(500L)).thenAnswer(inv -> readLines());
            when(lines.selectListByTaskId(500L)).thenAnswer(inv -> readLines());
            when(lines.updateById(any(RdLoadingTaskLineDO.class))).thenAnswer(inv -> {
                RdLoadingTaskLineDO line = inv.getArgument(0);
                return jdbc.update("UPDATE loading_line SET status=?,version=?,outbound_id=?,outbound_line_id=?,transaction_id=? WHERE id=?",
                        line.getLineStatus(), line.getVersion(), line.getOutboundOrderId(), line.getOutboundOrderLineId(), line.getStockTransactionId(), line.getId());
            });
            when(tasks.updateById(any(RdLoadingTaskDO.class))).thenAnswer(inv -> {
                RdLoadingTaskDO task = inv.getArgument(0);
                return jdbc.update("UPDATE loading_task SET status=?,version=?,scanned_count=?,scanned_weight=?,confirm_time=?,scan_complete_time=? WHERE id=500",
                        task.getStatus(), task.getVersion(), task.getScannedPackageCount(), task.getScannedWeight(), task.getConfirmTime(), task.getScanCompleteTime());
            });
            when(scans.selectByTaskAndIdempotencyKey(500L, "batch-one")).thenAnswer(inv -> {
                var records = jdbc.query("SELECT * FROM scan_record WHERE request_key='batch-one'", (rs, n) -> {
                    var record = new RdLoadingScanRecordDO();
                    record.setId(rs.getLong("id")); record.setTaskId(500L); record.setTaskLineId(600L);
                    record.setOperatorId(rs.getLong("operator_id")); record.setPackageCode(rs.getString("package_code"));
                    record.setSuccess(true); record.setScanResult("BATCH_CONFIRMED");
                    record.setRequestDigest(rs.getString("request_digest"));
                    return record;
                });
                return records.isEmpty() ? null : records.get(0);
            });
            when(scans.insert(any(RdLoadingScanRecordDO.class))).thenAnswer(inv -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                RdLoadingScanRecordDO record = inv.getArgument(0);
                record.setId(88L);
                return jdbc.update("INSERT INTO scan_record VALUES (88,?,?,?,?,?)", record.getIdempotencyKey(), record.getRequestDigest(),
                        record.getOperatorId(), record.getPackageCode(), record.getScanTime());
            });
            doAnswer(inv -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                return jdbc.update("INSERT INTO history VALUES (88)");
            }).when(history).append(any());
        }

        private List<RdLoadingTaskLineDO> readLines() {
            return jdbc.query("SELECT * FROM loading_line ORDER BY id", (rs, n) -> {
                long id = rs.getLong("id");
                var line = line(id, "PK" + id, rs.getString("status"), "1000");
                line.setVersion(rs.getInt("version"));
                line.setFulfillmentId(700L); line.setSaleOrderId(800L); line.setStockLotId(300L);
                line.setOutboundOrderId(rs.getObject("outbound_id", Long.class));
                line.setOutboundOrderLineId(rs.getObject("outbound_line_id", Long.class));
                line.setStockTransactionId(rs.getObject("transaction_id", Long.class));
                return line;
            });
        }

        void wireOutbound() {
            jdbc.update("UPDATE loading_task SET status='PENDING_REVIEW',scanned_count=2,scanned_weight=2000");
            jdbc.update("UPDATE loading_line SET status='SCANNED'");
            when(fulfillments.selectByIdForUpdate(700L)).thenReturn(new RdOrderFulfillmentDO()
                    .setId(700L).setOrderId(800L).setStockLotId(300L));
            when(stockLots.selectByIdForUpdate(300L)).thenReturn(new RdStockLotDO().setId(300L).setPackageWeight(new BigDecimal("1000")));
            when(packages.selectByIdForUpdate(anyLong())).thenAnswer(inv -> {
                long id = inv.getArgument(0);
                var unit = new RdPackageUnitDO().setId(id).setPackageCode("PK" + (id - 1000))
                        .setStockLotId(300L).setPackageWeight(new BigDecimal("1000"))
                        .setStatus(jdbc.queryForObject("SELECT status FROM package_unit WHERE id=?", String.class, id));
                unit.setTenantId(1L);
                return unit;
            });
            when(packages.updateById(any(RdPackageUnitDO.class))).thenAnswer(inv -> {
                RdPackageUnitDO unit = inv.getArgument(0);
                return jdbc.update("UPDATE package_unit SET status=? WHERE id=?", unit.getStatus(), unit.getId());
            });
            when(claims.selectByPackageIdForUpdate(anyLong())).thenAnswer(inv -> claim(inv.<Long>getArgument(0) - 1000));
            when(claims.selectListByTaskIdForUpdate(500L)).thenReturn(List.of(claim(600L), claim(601L)));
            when(claims.deleteById(anyLong())).thenAnswer(inv -> jdbc.update("DELETE FROM package_claim WHERE id=?", inv.<Long>getArgument(0)));
            when(outboundOrders.insert(any(RdOutboundOrderDO.class))).thenAnswer(inv -> {
                RdOutboundOrderDO order = inv.getArgument(0); order.setId(900L);
                return jdbc.update("INSERT INTO outbound_order VALUES (900,?)", order.getStatus());
            });
            when(outboundLines.insert(any(RdOutboundOrderLineDO.class))).thenAnswer(inv -> {
                RdOutboundOrderLineDO line = inv.getArgument(0); line.setId(line.getPackageId());
                return jdbc.update("INSERT INTO outbound_line (id,order_id,package_id) VALUES (?,?,?)", line.getId(), line.getOrderId(), line.getPackageId());
            });
            when(outboundLines.selectListByOrderId(900L)).thenAnswer(inv -> jdbc.query("SELECT * FROM outbound_line WHERE order_id=900", (rs, n) -> {
                var line = new RdOutboundOrderLineDO();
                line.setId(rs.getLong("id")); line.setOrderId(rs.getLong("order_id")); line.setPackageId(rs.getLong("package_id"));
                line.setStockTransactionId(rs.getObject("transaction_id", Long.class)); line.setPackageWeight(new BigDecimal("1000"));
                return line;
            }));
            when(outboundOrders.selectById(900L)).thenAnswer(inv -> new RdOutboundOrderDO().setId(900L)
                    .setStatus(jdbc.queryForObject("SELECT status FROM outbound_order WHERE id=900", String.class)));
            doAnswer(inv -> {
                fakedPosting();
                return null;
            }).when(outboundService).postOutboundOrder(eq(900L), isNull(), eq(20L), eq("warehouse"));
            var slip = new RdPickupSlipDO(); slip.setId(50L); slip.setStatus("DISPATCHED"); slip.setVersion(0);
            when(slips.selectByIdForUpdate(50L, 1L)).thenReturn(slip);
            when(slips.updateById(any(RdPickupSlipDO.class))).thenAnswer(inv -> jdbc.update("UPDATE pickup_slip SET status=? WHERE id=50", inv.<RdPickupSlipDO>getArgument(0).getStatus()));
        }

        private void fakedPosting() {
            jdbc.update("INSERT INTO stock_ledger VALUES (2600),(2601)");
            jdbc.update("UPDATE package_unit SET status='OUTBOUNDED'");
            jdbc.update("UPDATE outbound_order SET status='POSTED'");
            jdbc.update("UPDATE outbound_line SET transaction_id=package_id+1000");
        }

        private static RdLoadingPackageClaimDO claim(long id) {
            var claim = new RdLoadingPackageClaimDO();
            claim.setId(id); claim.setTaskId(500L); claim.setTaskLineId(id);
            return claim;
        }

        int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }

        List<List<Map<String, Object>>> snapshot() {
            return List.of(jdbc.queryForList("SELECT * FROM loading_task ORDER BY id"), jdbc.queryForList("SELECT * FROM loading_line ORDER BY id"),
                    jdbc.queryForList("SELECT * FROM scan_record ORDER BY id"), jdbc.queryForList("SELECT * FROM outbound_order ORDER BY id"),
                    jdbc.queryForList("SELECT * FROM outbound_line ORDER BY id"), jdbc.queryForList("SELECT * FROM package_unit ORDER BY id"),
                    jdbc.queryForList("SELECT * FROM package_claim ORDER BY id"), jdbc.queryForList("SELECT * FROM pickup_slip ORDER BY id"));
        }

        void assertBatchUnchanged() {
            assertEquals("ASSIGNED", jdbc.queryForObject("SELECT status FROM loading_task", String.class));
            assertEquals(0, jdbc.queryForObject("SELECT version FROM loading_task", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT scanned_count FROM loading_task", Integer.class));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM loading_line WHERE status='PLANNED' AND version=0", Integer.class));
            assertEquals(0, count("scan_record")); assertEquals(0, count("history"));
        }

        void assertOutboundUnchanged() {
            assertEquals("PENDING_REVIEW", jdbc.queryForObject("SELECT status FROM loading_task", String.class));
            assertEquals(0, jdbc.queryForObject("SELECT version FROM loading_task", Integer.class));
            assertNull(jdbc.queryForObject("SELECT confirm_time FROM loading_task", java.sql.Timestamp.class));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM loading_line WHERE status='SCANNED' AND outbound_id IS NULL AND transaction_id IS NULL", Integer.class));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM package_unit WHERE status='IN_STOCK'", Integer.class));
            assertEquals(2, count("package_claim"));
            assertEquals("DISPATCHED", jdbc.queryForObject("SELECT status FROM pickup_slip", String.class));
            assertEquals(0, count("outbound_order")); assertEquals(0, count("outbound_line")); assertEquals(0, count("stock_ledger"));
        }

        void clearWrites() { clearInvocations(tasks, lines, scans, history, packages, claims, slips, outboundOrders, outboundLines, outboundService); }

        void verifyNoWrites() {
            verify(tasks, never()).updateById(any(RdLoadingTaskDO.class));
            verify(lines, never()).updateById(any(RdLoadingTaskLineDO.class));
            verify(scans, never()).insert(any(RdLoadingScanRecordDO.class));
            verify(history, never()).append(any());
            verify(packages, never()).updateById(any(RdPackageUnitDO.class));
            verify(claims, never()).deleteById(anyLong());
            verify(slips, never()).updateById(any(RdPickupSlipDO.class));
            verify(outboundOrders, never()).insert(any(RdOutboundOrderDO.class));
            verify(outboundLines, never()).insert(any(RdOutboundOrderLineDO.class));
        }

        @Override public void close() throws SQLException { keepAlive.close(); TenantContextHolder.clear(); }
    }
}
