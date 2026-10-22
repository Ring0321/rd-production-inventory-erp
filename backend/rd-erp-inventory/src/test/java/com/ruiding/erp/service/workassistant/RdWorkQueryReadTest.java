package com.ruiding.erp.service.workassistant;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.ruiding.erp.dal.mysql.workassistant.RdWorkQueryMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static com.ruiding.erp.service.workassistant.RdWorkQueryContract.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RdWorkQueryReadTest {
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private RdWorkQueryService service;
    private PermissionService permissions;

    @BeforeEach void open() {
        TenantContextHolder.setTenantId(1L);
        var user = new LoginUser().setId(7L).setTenantId(1L).setUserType(UserTypeEnum.ADMIN.getValue());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        context = new AnnotationConfigApplicationContext(Config.class);
        jdbc = context.getBean(JdbcTemplate.class);
        service = context.getBean(RdWorkQueryService.class);
        permissions = context.getBean(PermissionService.class);
        when(permissions.hasAnyPermissions(eq(7L), anyString())).thenReturn(true);
        jdbc.execute("CREATE TABLE rd_package_unit (id BIGINT PRIMARY KEY,tenant_id BIGINT,package_code VARCHAR(32),"
                + "package_weight DECIMAL(24,6),status VARCHAR(32),version INT,update_time TIMESTAMP,deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE rd_quality_inspection (id BIGINT PRIMARY KEY,tenant_id BIGINT,inspection_no VARCHAR(64),"
                + "status VARCHAR(32),submit_time TIMESTAMP,version INT,update_time TIMESTAMP,deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE rd_quality_inspection_member (id BIGINT PRIMARY KEY,tenant_id BIGINT,inspection_id BIGINT,"
                + "package_id BIGINT,package_code VARCHAR(32),sampled INT,deleted INT DEFAULT 0)");
    }

    @AfterEach void close() {
        if (context != null) context.close();
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test void wireSnapshotTimeIsIsoEvenWhenApplicationDefaultsToTimestamps() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules()
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var wire = mapper.readTree(mapper.writeValueAsString(query(Query.Q03, 1, 20)));
        assertTrue(wire.get("asOf").isTextual());
        assertEquals("2026-10-22T01:00:00Z", wire.get("asOf").asText());
    }

    @Test void pendingPackagesUseExplicitTenantStatusPagingAndFullScopeTotals() {
        bag(1, 1, "QC_PENDING", "1000.123456");
        bag(2, 1, "QC_PENDING", "800");
        bag(3, 2, "QC_PENDING", "9000");
        bag(4, 1, "QC_PASSED", "1000");
        bag(5, 1, "QC_PENDING", "1000");
        jdbc.update("UPDATE rd_package_unit SET deleted=1 WHERE id=5");
        var result = query(Query.Q03, 2, 1);
        assertEquals(Quality.KNOWN, result.quality());
        assertEquals(2L, result.total());
        assertEquals(2L, metric(result, MetricKey.DISTINCT_PACKAGE_COUNT).longValueExact());
        assertEquals(new BigDecimal("1800.123456"), metric(result, MetricKey.REGISTERED_WEIGHT_KG));
        assertEquals(1, result.items().size());
        var item = (RdWorkQueryService.PackageItem) result.items().get(0);
        assertEquals("2", item.sourceId());
        assertEquals("QC_PENDING", item.status());
        assertEquals(4, item.sourceVersion());
        assertEquals(Instant.parse("2026-10-22T01:00:00Z"), result.asOf());
    }

    @Test void qualifiedAndInboundPendingRemainSeparateAndNeverIncludeStockOrRejected() {
        bag(10, 1, "QC_PASSED", "10");
        bag(11, 1, "INBOUND_PENDING", "11");
        bag(12, 1, "IN_STOCK", "12");
        bag(13, 1, "QC_REJECTED", "13");
        bag(14, 2, "INBOUND_PENDING", "14");
        var result = query(Query.Q05, 1, 20);
        assertEquals(2L, result.total());
        assertEquals(1, metric(result, MetricKey.QC_PASSED_PACKAGE_COUNT).intValueExact());
        assertEquals(1, metric(result, MetricKey.INBOUND_PENDING_PACKAGE_COUNT).intValueExact());
        assertEquals(0, metric(result, MetricKey.REGISTERED_WEIGHT_KG).compareTo(new BigDecimal("21")));
    }

    @Test void inspectionCountIsNotPackageCountAndCoverageUsesActualMembersOnly() {
        header(101, 1, "SUBMITTED");
        header(102, 1, "SUBMITTED");
        header(103, 1, "DRAFT");
        header(104, 2, "SUBMITTED");
        member(1, 1, 101, 10, true);
        member(2, 1, 101, 11, false);
        member(3, 1, 102, 11, true);
        member(4, 1, 102, 11, true);
        member(5, 1, 103, 88, true);
        member(6, 2, 104, 99, true);
        member(7, 2, 101, 100, true);
        var result = query(Query.Q04, 1, 1);
        assertEquals(Quality.KNOWN, result.quality());
        assertEquals(2L, result.total());
        assertEquals(2, metric(result, MetricKey.DISTINCT_PACKAGE_COUNT).intValueExact());
        var item = (RdWorkQueryService.InspectionItem) result.items().get(0);
        assertEquals("101", item.sourceId());
        assertEquals("2", item.coveredPackageCount());
        assertEquals("1", item.sampledPackageCount());
        assertEquals(2, item.members().size());
        assertTrue(item.members().stream().noneMatch(value -> value.packageId().equals("100")));
    }

    @Test void legacyMissingCoverageIsUnknownNotZeroOrTheWholeProductionBatch() {
        header(101, 1, "SUBMITTED");
        var result = query(Query.Q04, 1, 20);
        assertEquals(Quality.PARTIAL, result.quality());
        assertNull(result.total());
        assertEquals(1, metric(result, MetricKey.BUSINESS_OBJECT_COUNT).intValueExact());
        assertNull(result.metrics().get(MetricKey.DISTINCT_PACKAGE_COUNT).value());
        var item = (RdWorkQueryService.InspectionItem) result.items().get(0);
        assertEquals(Quality.UNAVAILABLE, item.coverageQuality());
        assertNull(item.coveredPackageCount());
        assertTrue(item.members().isEmpty());
    }

    @Test void missingTimeAndInvalidWeightsAreReportedAsPartialWithoutInventedValues() {
        bag(1, 1, "QC_PENDING", null);
        var result = query(Query.Q03, 1, 20);
        assertEquals(Quality.PARTIAL, result.quality());
        assertNull(result.metrics().get(MetricKey.REGISTERED_WEIGHT_KG).value());
        assertEquals(1, metric(result, MetricKey.DISTINCT_PACKAGE_COUNT).intValueExact());
        header(101, 1, "SUBMITTED");
        member(1, 1, 101, 1, true);
        jdbc.update("UPDATE rd_quality_inspection SET submit_time=NULL WHERE id=101");
        result = query(Query.Q04, 1, 20);
        assertEquals(Quality.PARTIAL, result.quality());
        assertNull(((RdWorkQueryService.InspectionItem) result.items().get(0)).submittedAt());
        assertFalse(result.notices().isEmpty());
    }

    @Test void memberPreviewIsBoundedWhileCountsCoverTheWholeInspection() {
        header(101, 1, "SUBMITTED");
        for (int index = 1; index <= 25; index++) member(index, 1, 101, index, index == 1);
        var result = query(Query.Q04, 1, 20);
        var item = (RdWorkQueryService.InspectionItem) result.items().get(0);
        assertEquals("25", item.coveredPackageCount());
        assertEquals(8, item.members().size());
        assertTrue(item.membersTruncated());
        assertEquals(25, metric(result, MetricKey.DISTINCT_PACKAGE_COUNT).intValueExact());
    }

    @Test void confirmedEmptyIsKnownZeroAndReadsLeaveAllSourceRowsUntouched() {
        for (Query query : List.of(Query.Q03, Query.Q04, Query.Q05)) {
            var result = query(query, 1, 20);
            assertEquals(Quality.KNOWN, result.quality());
            assertEquals(0L, result.total());
            assertTrue(result.metrics().values().stream().allMatch(value -> value.value().signum() == 0));
        }
        bag(1, 1, "QC_PENDING", "1000");
        header(101, 1, "SUBMITTED");
        member(1, 1, 101, 1, true);
        var before = snapshot();
        query(Query.Q03, 1, 20);
        query(Query.Q04, 1, 20);
        query(Query.Q05, 1, 20);
        assertEquals(before, snapshot());
    }

    @Test void missingSourceFailsClosedWithoutSqlOrFakeZeros() {
        jdbc.execute("DROP TABLE rd_package_unit");
        var result = query(Query.Q03, 1, 20);
        assertEquals(Quality.UNAVAILABLE, result.quality());
        assertNull(result.total());
        assertTrue(result.items().isEmpty());
        assertTrue(result.metrics().values().stream().allMatch(value -> value.value() == null));
        assertFalse(result.notices().toString().contains("SELECT"));
    }

    @Test void driverAndUnsupportedQueriesAreRejectedBeforeReadingSources() {
        when(permissions.hasAnyRoles(7L, "rd_forklift_driver")).thenReturn(true);
        assertEquals(403, assertThrows(ServiceException.class, () -> query(Query.Q03, 1, 20)).getCode());
        when(permissions.hasAnyRoles(7L, "rd_forklift_driver")).thenReturn(false);
        assertEquals(503, assertThrows(ServiceException.class, () -> query(Query.Q01, 1, 20)).getCode());
    }

    private Result<?> query(Query query, int pageNo, int pageSize) {
        return service.query(new Request(query, null, null, pageNo, pageSize));
    }

    private static BigDecimal metric(Result<?> result, MetricKey key) { return result.metrics().get(key).value(); }
    private List<?> snapshot() {
        return List.of(jdbc.queryForList("SELECT * FROM rd_package_unit ORDER BY id"),
                jdbc.queryForList("SELECT * FROM rd_quality_inspection ORDER BY id"),
                jdbc.queryForList("SELECT * FROM rd_quality_inspection_member ORDER BY id"));
    }
    private void bag(long id, long tenant, String status, String weight) {
        jdbc.update("INSERT INTO rd_package_unit VALUES (?,?,?,?,?,4,CURRENT_TIMESTAMP,0)", id, tenant,
                "LD20261022A" + String.format("%04d", id), weight == null ? null : new BigDecimal(weight), status);
    }
    private void header(long id, long tenant, String status) {
        jdbc.update("INSERT INTO rd_quality_inspection VALUES (?,?,?,?,CURRENT_TIMESTAMP,5,CURRENT_TIMESTAMP,0)", id, tenant, "QI-" + id, status);
    }
    private void member(long id, long tenant, long inspection, long bag, boolean sampled) {
        jdbc.update("INSERT INTO rd_quality_inspection_member VALUES (?,?,?,?,?,?,0)", id, tenant, inspection,
                bag, "LD20261022A" + String.format("%04d", bag), sampled ? 1 : 0);
    }

    @Configuration @EnableTransactionManagement
    static class Config {
        @Bean DataSource source() { return new DriverManagerDataSource("jdbc:h2:mem:workquery_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""); }
        @Bean JdbcTemplate jdbc(DataSource source) { return new JdbcTemplate(source); }
        @Bean DataSourceTransactionManager transactions(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean SqlSessionFactory sessions(DataSource source) throws Exception {
            var config = new MybatisConfiguration();
            config.setMapUnderscoreToCamelCase(true);
            config.setLogImpl(NoLoggingImpl.class);
            config.addMapper(RdWorkQueryMapper.class);
            var factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(source);
            factory.setConfiguration(config);
            return factory.getObject();
        }
        @Bean RdWorkQueryMapper mapper(SqlSessionFactory sessions) { return new SqlSessionTemplate(sessions).getMapper(RdWorkQueryMapper.class); }
        @Bean PermissionService permissions() { return mock(PermissionService.class); }
        @Bean RdWorkQueryAccessPolicy policy(PermissionService permissions) {
            var policy = new RdWorkQueryAccessPolicy();
            ReflectionTestUtils.setField(policy, "permissions", permissions);
            return policy;
        }
        @Bean RdWorkQueryService service(RdWorkQueryAccessPolicy policy, RdWorkQueryMapper mapper) {
            return new RdWorkQueryService(policy, mapper, Clock.fixed(Instant.parse("2026-10-22T01:00:00Z"), ZoneOffset.UTC));
        }
    }
}
