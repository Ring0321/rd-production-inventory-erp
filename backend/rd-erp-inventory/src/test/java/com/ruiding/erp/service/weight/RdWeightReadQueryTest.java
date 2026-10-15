package com.ruiding.erp.service.weight;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.ruiding.erp.dal.dataobject.masterdata.RdWarehouseLocationDO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockLotDO;
import com.ruiding.erp.dal.mysql.loading.RdLoadingPackageClaimMapper;
import com.ruiding.erp.dal.mysql.masterdata.RdWarehouseLocationMapper;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.dal.mysql.quality.RdQualityInspectionMapper;
import com.ruiding.erp.dal.mysql.stock.RdStockLotMapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.ruiding.erp.service.weight.RdWeightAssistantServiceTest.*;
import static com.ruiding.erp.service.weight.RdWeightSolverTest.kg;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RdWeightReadQueryTest {
    @ParameterizedTest
    @ValueSource(strings = {"allocated", "foreign-tenant", "deleted-line", "inactive-allocation", "mismatched-package"})
    void executesRealReservationSelectsAgainstDisposableMemoryOnly(String scenario) throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:weight_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        try {
            jdbc.execute("CREATE TABLE rd_package_unit (id BIGINT PRIMARY KEY, tenant_id BIGINT, stock_lot_id BIGINT, deleted INT)");
            jdbc.execute("CREATE TABLE rd_loading_package_claim (package_id BIGINT, tenant_id BIGINT, task_id BIGINT, task_line_id BIGINT)");
            jdbc.execute("CREATE TABLE rd_loading_task_line (id BIGINT, tenant_id BIGINT, task_id BIGINT, package_id BIGINT, "
                    + "stock_lot_id BIGINT, fulfillment_id BIGINT, sale_order_id BIGINT, deleted INT)");
            jdbc.execute("CREATE TABLE rd_order_fulfillment (id BIGINT, tenant_id BIGINT, stock_lot_id BIGINT, order_id BIGINT, "
                    + "status VARCHAR(30), allocated_qty DECIMAL, outbound_qty DECIMAL, deleted INT)");
            jdbc.update("INSERT INTO rd_package_unit VALUES (1,1,10,0),(2,1,10,0),(3,1,10,0),(4,2,10,0)");
            jdbc.update("INSERT INTO rd_loading_package_claim VALUES (2,1,100,200),(3,1,101,201),(4,2,102,202)");
            jdbc.update("INSERT INTO rd_loading_task_line VALUES (200,1,100,2,10,80,90,0),(201,1,101,3,10,NULL,NULL,0)");
            jdbc.update("INSERT INTO rd_order_fulfillment VALUES (80,1,10,90,'ALLOCATED',1,0,0)");
            switch (scenario) {
                case "foreign-tenant" -> jdbc.update("UPDATE rd_loading_task_line SET tenant_id=2 WHERE id=200");
                case "deleted-line" -> jdbc.update("UPDATE rd_loading_task_line SET deleted=1 WHERE id=200");
                case "inactive-allocation" -> jdbc.update("UPDATE rd_order_fulfillment SET status='CANCELLED'");
                case "mismatched-package" -> jdbc.update("UPDATE rd_loading_task_line SET package_id=99 WHERE id=200");
                default -> { }
            }
            var configuration = new MybatisConfiguration();
            configuration.setEnvironment(new Environment("weight-read-test", new JdbcTransactionFactory(), source));
            configuration.addMapper(RdPackageUnitMapper.class);
            var factory = new MybatisSqlSessionFactoryBuilder().build(configuration);
            try (var session = factory.openSession()) {
                var realPackages = session.getMapper(RdPackageUnitMapper.class);
                var packages = mock(RdPackageUnitMapper.class);
                var lots = mock(RdStockLotMapper.class);
                var locations = mock(RdWarehouseLocationMapper.class);
                var quality = mock(RdQualityInspectionMapper.class);
                var reader = new RdWeightInventoryReader();
                ReflectionTestUtils.setField(reader, "packageMapper", packages);
                ReflectionTestUtils.setField(reader, "stockLotMapper", lots);
                ReflectionTestUtils.setField(reader, "locationMapper", locations);
                ReflectionTestUtils.setField(reader, "qualityMapper", quality);
                ReflectionTestUtils.setField(reader, "claimMapper", mock(RdLoadingPackageClaimMapper.class));
                var unit = new RdPackageUnitDO().setId(1L).setPackageCode("PK1").setCustomerName("CUSTOMER")
                        .setProductModel("MODEL").setStatus("IN_STOCK").setPackageWeight(kg("500"))
                        .setLocationId(20L).setLocationCode("A-01").setStockLotId(10L).setProductionOutputId(30L);
                unit.setTenantId(1L);
                var lot = new RdStockLotDO().setId(10L).setLocationId(20L).setLocationCode("A-01")
                        .setCustomerName("CUSTOMER").setProductModel("MODEL").setStatus("AVAILABLE")
                        .setWarehouseQty(kg("4")).setLockedQty(kg("1"));
                lot.setTenantId(1L);
                var location = new RdWarehouseLocationDO().setId(20L).setLocationCode("A-01")
                        .setEnabled(true).setFrozen(false).setLocationStatus("AVAILABLE");
                location.setTenantId(1L);
                var inspection = new RdQualityInspectionDO().setId(40L).setProductionOutputId(30L)
                        .setStatus("PASSED").setConclusion("PASSED").setReviewerId(7L).setReviewTime(LocalDateTime.now());
                inspection.setTenantId(1L);
                when(packages.selectList(any(Wrapper.class))).thenReturn(List.of(unit));
                when(packages.selectCount(any(Wrapper.class))).thenAnswer(call -> realPackages.selectCount(call.getArgument(0)));
                when(lots.selectById(10L)).thenReturn(lot);
                when(locations.selectById(20L)).thenReturn(location);
                when(quality.selectListByCoveredPackageId(1L)).thenReturn(List.of(inspection));
                var before = jdbc.queryForList("SELECT * FROM rd_loading_package_claim ORDER BY package_id");
                var inventory = reader.readCandidates(slip(), line(11));
                assertTrue(inventory.complete());
                assertEquals(scenario.equals("allocated") ? 2 : 1, inventory.lotCaps().get(10L));
                assertEquals(before, jdbc.queryForList("SELECT * FROM rd_loading_package_claim ORDER BY package_id"));
                assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM rd_package_unit", Integer.class));
            }
        } finally {
            try (var connection = source.getConnection(); var statement = connection.createStatement()) {
                statement.execute("SHUTDOWN");
            }
        }
    }
}
