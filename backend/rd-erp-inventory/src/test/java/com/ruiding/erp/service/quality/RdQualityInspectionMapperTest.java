package com.ruiding.erp.service.quality;

import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionDO;
import com.ruiding.erp.dal.mysql.quality.RdQualityInspectionMapper;
import com.ruiding.erp.dal.mysql.quality.RdQualityInspectionMemberMapper;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RdQualityInspectionMapperTest {
    private JdbcTemplate jdbc;
    private SqlSession session;
    private RdQualityInspectionMapper inspections;
    private RdQualityInspectionMemberMapper members;

    @BeforeEach void openIsolatedDatabase() throws Exception {
        TenantContextHolder.setTenantId(1L);
        var source = new DriverManagerDataSource("jdbc:h2:mem:d3_mapper_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE rd_quality_inspection (id BIGINT PRIMARY KEY,tenant_id BIGINT,inspection_no VARCHAR(64),"
                + "package_id BIGINT,production_output_id BIGINT,client_request_id VARCHAR(128),request_digest CHAR(64),"
                + "registrar_id BIGINT,registrar_name VARCHAR(64),status VARCHAR(32),conclusion VARCHAR(32),reject_reason VARCHAR(512),"
                + "submitter_id BIGINT,submitter_name VARCHAR(64),submit_time TIMESTAMP,reviewer_id BIGINT,reviewer_name VARCHAR(64),review_time TIMESTAMP,"
                + "version INT,remark VARCHAR(512),creator VARCHAR(64),create_time TIMESTAMP,updater VARCHAR(64),update_time TIMESTAMP,deleted INT DEFAULT 0,"
                + "UNIQUE(tenant_id,client_request_id))");
        jdbc.execute("CREATE TABLE rd_quality_inspection_member (id BIGINT PRIMARY KEY,tenant_id BIGINT,inspection_id BIGINT,"
                + "package_id BIGINT,package_code VARCHAR(32),package_weight DECIMAL(24,6),sampled BOOLEAN,active_key BIGINT,"
                + "creator VARCHAR(64),create_time TIMESTAMP,updater VARCHAR(64),update_time TIMESTAMP,deleted INT DEFAULT 0,"
                + "UNIQUE(tenant_id,inspection_id,package_id),UNIQUE(tenant_id,active_key))");
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(NoLoggingImpl.class);
        configuration.addMapper(RdQualityInspectionMapper.class);
        configuration.addMapper(RdQualityInspectionMemberMapper.class);
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(configuration);
        factory.setPlugins(interceptor);
        session = factory.getObject().openSession(true);
        inspections = session.getMapper(RdQualityInspectionMapper.class);
        members = session.getMapper(RdQualityInspectionMemberMapper.class);
    }

    @AfterEach void close() {
        if (session != null) session.close();
        TenantContextHolder.clear();
    }

    @Test void shouldQueryRealMembershipOrOnlyLegacyRepresentativeWithTenantIsolation() {
        header(1, 1, 10, "PASSED");
        header(2, 1, 20, "PASSED");
        member(21, 1, 2, 10, false, null);
        member(22, 1, 2, 20, true, null);
        header(3, 1, 30, "PASSED");
        member(31, 1, 3, 20, true, null);
        header(4, 2, 10, "PASSED");
        member(41, 2, 4, 10, true, null);

        assertEquals(List.of(1L, 2L), ids(inspections.selectListByCoveredPackageId(10L)));
        assertEquals(List.of(2L, 3L), ids(inspections.selectListByCoveredPackageId(20L)));
        assertTrue(inspections.selectListByCoveredPackageId(30L).isEmpty());
        assertTrue(inspections.selectListByCoveredPackageId(40L).isEmpty());
        TenantContextHolder.setTenantId(2L);
        assertEquals(List.of(4L), ids(inspections.selectListByCoveredPackageId(10L)));
    }

    @Test void shouldFindActiveCoverageAndLegacyDraftButNotUnselectedRepresentative() {
        header(5, 1, 50, "DRAFT");
        member(51, 1, 5, 10, true, 10L);
        header(6, 1, 20, "CORRECTED");
        header(7, 2, 10, "SUBMITTED");

        assertEquals(5L, inspections.selectActiveByPackageId(10L, null).getId());
        assertNull(inspections.selectActiveByPackageId(10L, 5L));
        assertEquals(6L, inspections.selectActiveByPackageId(20L, null).getId());
        assertNull(inspections.selectActiveByPackageId(50L, null));
    }

    @Test void shouldEnforceUniqueTenantClaimAndPreserveHistoryWhenReleased() {
        member(1, 1, 100, 10, true, 10L);
        assertThrows(DuplicateKeyException.class, () -> member(2, 1, 200, 10, false, 10L));
        member(3, 2, 300, 10, true, 10L);
        assertEquals(1, members.releaseActiveByInspectionId(100L));
        assertEquals(0, members.releaseActiveByInspectionId(100L));
        member(4, 1, 400, 10, false, 10L);
        assertNull(members.selectListByInspectionId(100L).get(0).getActiveKey());
        assertTrue(members.selectListByInspectionId(100L).get(0).getSampled());
        assertEquals(10L, jdbc.queryForObject("SELECT active_key FROM rd_quality_inspection_member WHERE id=3", Long.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM rd_quality_inspection_member", Integer.class));
    }

    @Test void shouldReadCommittedReplayFreshEvenWithMybatisSessionCache() {
        assertNull(inspections.selectByClientRequestId("lost-response"));
        header(8, 1, 10, "PASSED");
        jdbc.update("UPDATE rd_quality_inspection SET client_request_id='lost-response',registrar_id=100 WHERE id=8");
        RdQualityInspectionDO replay = inspections.selectByClientRequestId("lost-response");
        assertNotNull(replay);
        assertEquals(8L, replay.getId());
        assertEquals("PASSED", replay.getStatus());
    }

    private void header(long id, long tenant, long representative, String status) {
        jdbc.update("INSERT INTO rd_quality_inspection (id,tenant_id,package_id,production_output_id,status) VALUES (?,?,?,101,?)",
                id, tenant, representative, status);
    }

    private void member(long id, long tenant, long inspection, long packageId, boolean sampled, Long active) {
        jdbc.update("INSERT INTO rd_quality_inspection_member (id,tenant_id,inspection_id,package_id,package_code,package_weight,sampled,active_key) "
                + "VALUES (?,?,?,?,?,1000,?,?)", id, tenant, inspection, packageId, "PKG-" + packageId, sampled, active);
    }

    private static List<Long> ids(List<RdQualityInspectionDO> rows) {
        return rows.stream().map(RdQualityInspectionDO::getId).toList();
    }
}
