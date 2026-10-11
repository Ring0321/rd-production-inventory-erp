package com.ruiding.erp.service.quality;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionDO;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionItemDO;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionMemberDO;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionEventDO;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.dal.mysql.production.RdProductionOutputMapper;
import com.ruiding.erp.dal.mysql.production.RdProductionTaskMapper;
import com.ruiding.erp.dal.mysql.quality.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static com.ruiding.erp.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.ruiding.erp.service.quality.RdQualityInspectionBatchTest.*;

class RdQualityInspectionTransactionTest {
    @AfterEach void clearLogin() { SecurityContextHolder.clearContext(); }

    @Test void shouldRollbackDraftHeaderMembersAndItemsOnLateItemFailure() {
        try (var context = context()) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            wireCreate(context);
            when(context.getBean(RdQualityInspectionItemMapper.class).insert(any(RdQualityInspectionItemDO.class)))
                    .thenAnswer(invocation -> {
                        jdbc.update("INSERT INTO qc_item (id) VALUES (1)");
                        throw new DataIntegrityViolationException("late item write failed");
                    });

            assertThrows(DataIntegrityViolationException.class, () -> context.getBean(RdQualityInspectionService.class).createBatch(request()));

            assertEquals(0, count(jdbc, "qc_header"));
            assertEquals(0, count(jdbc, "qc_member"));
            assertEquals(0, count(jdbc, "qc_item"));
            assertEquals("LABELED", status(jdbc, 1L));
            assertEquals("LABELED", status(jdbc, 3L));
        }
    }

    @Test void shouldRollbackEarlierClaimsAndHeaderOnUniqueActiveClaimFailure() {
        try (var context = context()) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            jdbc.update("INSERT INTO qc_member (tenant_id,inspection_id,package_id,active_key) VALUES (1,900,3,3)");
            wireCreate(context);

            ServiceException error = assertThrows(ServiceException.class,
                    () -> context.getBean(RdQualityInspectionService.class).createBatch(request()));

            assertEquals(QUALITY_INSPECTION_PACKAGE_CLAIMED.getCode(), error.getCode());
            assertEquals(0, count(jdbc, "qc_header"));
            assertEquals(1, count(jdbc, "qc_member"));
            assertEquals(900L, jdbc.queryForObject("SELECT inspection_id FROM qc_member WHERE active_key=3", Long.class));
        }
    }

    @Test void shouldRollbackEarlierPackageUpdateWhenAnotherMemberUpdateFails() {
        try (var context = context()) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            wireLifecycle(context, "DRAFT", "LABELED");
            when(context.getBean(RdPackageUnitMapper.class).updateById(any(RdPackageUnitDO.class)))
                    .thenAnswer(invocation -> {
                        RdPackageUnitDO unit = invocation.getArgument(0);
                        if (unit.getId().equals(3L)) return 0;
                        return jdbc.update("UPDATE qc_package SET status=? WHERE id=?", unit.getStatus(), unit.getId());
                    });

            ServiceException error = assertThrows(ServiceException.class,
                    () -> context.getBean(RdQualityInspectionService.class).submit(500L, 100L, "ignored"));

            assertEquals(QUALITY_INSPECTION_WRITE_CONFLICT.getCode(), error.getCode());
            assertEquals("LABELED", status(jdbc, 1L));
            assertEquals("LABELED", status(jdbc, 3L));
            assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM qc_header", String.class));
            assertNull(jdbc.queryForObject("SELECT submit_time FROM qc_header", java.sql.Timestamp.class));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM qc_member WHERE active_key IS NOT NULL", Integer.class));
        }
    }

    @Test void shouldRollbackPackagesReviewEvidenceAndPartialClaimReleaseTogether() {
        try (var context = context()) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            wireLifecycle(context, "SUBMITTED", "QC_PENDING");
            login(200L);
            when(context.getBean(RdPackageUnitMapper.class).updateById(any(RdPackageUnitDO.class)))
                    .thenAnswer(invocation -> {
                        RdPackageUnitDO unit = invocation.getArgument(0);
                        return jdbc.update("UPDATE qc_package SET status=? WHERE id=?", unit.getStatus(), unit.getId());
                    });
            when(context.getBean(RdQualityInspectionMapper.class).updateById(any(RdQualityInspectionDO.class)))
                    .thenAnswer(invocation -> {
                        RdQualityInspectionDO inspection = invocation.getArgument(0);
                        return jdbc.update("UPDATE qc_header SET status=?,review_time=?,reviewer_id=? WHERE id=?",
                                inspection.getStatus(), inspection.getReviewTime(), inspection.getReviewerId(), inspection.getId());
                    });
            when(context.getBean(RdQualityInspectionMemberMapper.class).releaseActiveByInspectionId(500L))
                    .thenAnswer(invocation -> jdbc.update("UPDATE qc_member SET active_key=NULL WHERE package_id=1"));

            ServiceException error = assertThrows(ServiceException.class,
                    () -> context.getBean(RdQualityInspectionService.class).approve(500L, 200L, "ignored"));

            assertEquals(QUALITY_INSPECTION_WRITE_CONFLICT.getCode(), error.getCode());
            assertEquals("QC_PENDING", status(jdbc, 1L));
            assertEquals("QC_PENDING", status(jdbc, 3L));
            assertEquals("SUBMITTED", jdbc.queryForObject("SELECT status FROM qc_header", String.class));
            assertNull(jdbc.queryForObject("SELECT review_time FROM qc_header", java.sql.Timestamp.class));
            assertNull(jdbc.queryForObject("SELECT reviewer_id FROM qc_header", Long.class));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM qc_member WHERE active_key IS NOT NULL", Integer.class));
        }
    }

    private static AnnotationConfigApplicationContext context() {
        login(100L);
        var context = new AnnotationConfigApplicationContext(Config.class);
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        jdbc.execute("CREATE TABLE qc_package (id BIGINT PRIMARY KEY,status VARCHAR(32))");
        jdbc.execute("CREATE TABLE qc_header (id BIGINT PRIMARY KEY,status VARCHAR(32),submit_time TIMESTAMP,review_time TIMESTAMP,reviewer_id BIGINT)");
        jdbc.execute("CREATE TABLE qc_member (tenant_id BIGINT,inspection_id BIGINT,package_id BIGINT,active_key BIGINT,UNIQUE(tenant_id,active_key))");
        jdbc.execute("CREATE TABLE qc_item (id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE qc_event (id BIGINT AUTO_INCREMENT PRIMARY KEY,inspection_id BIGINT,action VARCHAR(16))");
        when(context.getBean(RdQualityInspectionEventMapper.class).insert(any(RdQualityInspectionEventDO.class)))
                .thenAnswer(invocation -> {
                    RdQualityInspectionEventDO event = invocation.getArgument(0);
                    return jdbc.update("INSERT INTO qc_event (inspection_id,action) VALUES (?,?)", event.getInspectionId(), event.getAction());
                });
        jdbc.update("INSERT INTO qc_package VALUES (1,'LABELED'),(3,'LABELED')");
        return context;
    }

    @Test void shouldRollbackAllCreatedRowsIfRequiredAuditWriteFails() {
        try (var context = context()) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            wireCreate(context);
            when(context.getBean(RdQualityInspectionItemMapper.class).insert(any(RdQualityInspectionItemDO.class)))
                    .thenAnswer(invocation -> jdbc.update("INSERT INTO qc_item VALUES (1)"));
            failAudit(context);
            assertThrows(DataIntegrityViolationException.class,
                    () -> context.getBean(RdQualityInspectionService.class).createBatch(request()));
            for (String table : List.of("qc_header", "qc_member", "qc_item", "qc_event")) assertEquals(0, count(jdbc, table));
        }
    }

    @Test void shouldRollbackSubmittedStateAndEvidenceIfRequiredAuditWriteFails() {
        try (var context = context()) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            wireLifecycle(context, "DRAFT", "LABELED");
            wireSuccessfulPackageAndHeaderWrites(context);
            failAudit(context);
            assertThrows(DataIntegrityViolationException.class,
                    () -> context.getBean(RdQualityInspectionService.class).submit(500L, 100L, "ignored"));
            assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM qc_header", String.class));
            assertNull(jdbc.queryForObject("SELECT submit_time FROM qc_header", java.sql.Timestamp.class));
            assertEquals("LABELED", status(jdbc, 1L));
            assertEquals("LABELED", status(jdbc, 3L));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM qc_member WHERE active_key IS NOT NULL", Integer.class));
            assertEquals(0, count(jdbc, "qc_event"));
        }
    }

    @Test void shouldRollbackBothReviewOutcomesAndReleasedClaimsIfRequiredAuditWriteFails() {
        for (boolean pass : List.of(true, false)) {
            try (var context = context()) {
                JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
                wireLifecycle(context, "SUBMITTED", "QC_PENDING");
                login(200L);
                wireSuccessfulPackageAndHeaderWrites(context);
                when(context.getBean(RdQualityInspectionMemberMapper.class).releaseActiveByInspectionId(500L))
                        .thenAnswer(invocation -> jdbc.update("UPDATE qc_member SET active_key=NULL WHERE inspection_id=500"));
                failAudit(context);
                RdQualityInspectionService service = context.getBean(RdQualityInspectionService.class);
                assertThrows(DataIntegrityViolationException.class, () -> {
                    if (pass) service.approve(500L, 200L, "ignored");
                    else service.reject(500L, 200L, "ignored", "measured result rejected");
                });
                assertEquals("SUBMITTED", jdbc.queryForObject("SELECT status FROM qc_header", String.class));
                assertNull(jdbc.queryForObject("SELECT review_time FROM qc_header", java.sql.Timestamp.class));
                assertNull(jdbc.queryForObject("SELECT reviewer_id FROM qc_header", Long.class));
                assertEquals("QC_PENDING", status(jdbc, 1L));
                assertEquals("QC_PENDING", status(jdbc, 3L));
                assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM qc_member WHERE active_key IS NOT NULL", Integer.class));
                assertEquals(0, count(jdbc, "qc_event"));
            }
        }
    }

    private static void failAudit(AnnotationConfigApplicationContext context) {
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        when(context.getBean(RdQualityInspectionEventMapper.class).insert(any(RdQualityInspectionEventDO.class)))
                .thenAnswer(invocation -> {
                    jdbc.update("INSERT INTO qc_event (inspection_id,action) VALUES (500,'FAIL')");
                    throw new DataIntegrityViolationException("required audit write failed");
                });
    }

    private static void wireSuccessfulPackageAndHeaderWrites(AnnotationConfigApplicationContext context) {
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        when(context.getBean(RdPackageUnitMapper.class).updateById(any(RdPackageUnitDO.class)))
                .thenAnswer(invocation -> {
                    RdPackageUnitDO unit = invocation.getArgument(0);
                    return jdbc.update("UPDATE qc_package SET status=? WHERE id=?", unit.getStatus(), unit.getId());
                });
        when(context.getBean(RdQualityInspectionMapper.class).updateById(any(RdQualityInspectionDO.class)))
                .thenAnswer(invocation -> {
                    RdQualityInspectionDO inspection = invocation.getArgument(0);
                    return jdbc.update("UPDATE qc_header SET status=?,submit_time=?,review_time=?,reviewer_id=? WHERE id=?",
                            inspection.getStatus(), inspection.getSubmitTime(), inspection.getReviewTime(), inspection.getReviewerId(), inspection.getId());
                });
    }

    private static void wireCreate(AnnotationConfigApplicationContext context) {
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        var packages = context.getBean(RdPackageUnitMapper.class);
        var outputs = context.getBean(RdProductionOutputMapper.class);
        when(packages.selectById(1L)).thenReturn(unit(1L, "LABELED"));
        when(packages.selectByIdForUpdate(anyLong())).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(java.sql.Connection.TRANSACTION_READ_COMMITTED,
                    TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
            Long id = invocation.getArgument(0);
            return unit(id, jdbc.queryForObject("SELECT status FROM qc_package WHERE id=? FOR UPDATE", String.class, id));
        });
        when(outputs.selectById(101L)).thenReturn(output());
        when(outputs.selectByIdForUpdate(101L)).thenReturn(output());
        when(context.getBean(RdQualityInspectionMapper.class).insert(any(RdQualityInspectionDO.class)))
                .thenAnswer(invocation -> {
                    invocation.<RdQualityInspectionDO>getArgument(0).setId(500L);
                    return jdbc.update("INSERT INTO qc_header (id,status) VALUES (500,'DRAFT')");
                });
        when(context.getBean(RdQualityInspectionMemberMapper.class).insert(any(RdQualityInspectionMemberDO.class)))
                .thenAnswer(invocation -> {
                    RdQualityInspectionMemberDO member = invocation.getArgument(0);
                    return jdbc.update("INSERT INTO qc_member VALUES (1,?,?,?)", member.getInspectionId(), member.getPackageId(), member.getActiveKey());
                });
    }

    private static void wireLifecycle(AnnotationConfigApplicationContext context, String headerStatus, String packageStatus) {
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        jdbc.update("INSERT INTO qc_header (id,status) VALUES (500,?)", headerStatus);
        jdbc.update("UPDATE qc_package SET status=?", packageStatus);
        jdbc.update("INSERT INTO qc_member VALUES (1,500,1,1),(1,500,3,3)");
        when(context.getBean(RdQualityInspectionMapper.class).selectByIdForUpdate(500L))
                .thenAnswer(invocation -> new RdQualityInspectionDO().setId(500L).setPackageId(1L).setProductionOutputId(101L)
                        .setClientRequestId("request-1").setSubmitterId(100L)
                        .setStatus(jdbc.queryForObject("SELECT status FROM qc_header WHERE id=500 FOR UPDATE", String.class)));
        when(context.getBean(RdQualityInspectionMemberMapper.class).selectListByInspectionId(500L)).thenReturn(members());
        when(context.getBean(RdQualityInspectionItemMapper.class).selectListByInspectionId(500L)).thenReturn(List.of(item(1L)));
        when(context.getBean(RdPackageUnitMapper.class).selectByIdForUpdate(anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            return unit(id, jdbc.queryForObject("SELECT status FROM qc_package WHERE id=? FOR UPDATE", String.class, id));
        });
    }

    private static int count(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
    private static String status(JdbcTemplate jdbc, Long id) {
        return jdbc.queryForObject("SELECT status FROM qc_package WHERE id=?", String.class, id);
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:d3_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean PlatformTransactionManager transactionManager(DataSource dataSource) { return new DataSourceTransactionManager(dataSource); }
        @Bean RdQualityInspectionServiceImpl qualityService() { return new RdQualityInspectionServiceImpl(); }
        @Bean RdQualityInspectionMapper inspectionMapper() { return mock(RdQualityInspectionMapper.class); }
        @Bean RdQualityInspectionMemberMapper memberMapper() { return mock(RdQualityInspectionMemberMapper.class); }
        @Bean RdQualityInspectionEventMapper eventMapper() { return mock(RdQualityInspectionEventMapper.class); }
        @Bean RdQualityInspectionItemMapper itemMapper() { return mock(RdQualityInspectionItemMapper.class); }
        @Bean RdPackageUnitMapper packageMapper() { return mock(RdPackageUnitMapper.class); }
        @Bean RdProductionOutputMapper outputMapper() { return mock(RdProductionOutputMapper.class); }
        @Bean RdProductionTaskMapper productionTaskMapper() { return mock(RdProductionTaskMapper.class); }
    }
}
