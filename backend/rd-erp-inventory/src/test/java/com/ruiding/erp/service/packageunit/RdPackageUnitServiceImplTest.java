package com.ruiding.erp.service.packageunit;

import com.ruiding.erp.controller.admin.packageunit.vo.RdPackageTraceRespVO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockTransactionDO;
import com.ruiding.erp.dal.mysql.packageunit.RdLabelPrintLogMapper;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.dal.mysql.production.RdProductionOutputMapper;
import com.ruiding.erp.dal.dataobject.production.RdProductionOutputDO;
import com.ruiding.erp.dal.mysql.quality.RdQualityInspectionMapper;
import com.ruiding.erp.dal.mysql.stock.RdStockTransactionMapper;
import com.ruiding.erp.enums.RdPackageStatusEnum;
import com.ruiding.erp.enums.RdProductionOutputStatusEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class RdPackageUnitServiceImplTest {

    @Mock private RdPackageUnitMapper packageMapper;
    @Mock private RdProductionOutputMapper productionMapper;
    @Mock private RdLabelPrintLogMapper printLogMapper;
    @Mock private RdDigitalLinkService digitalLinkService;
    @Mock private RdQualityInspectionMapper qualityInspectionMapper;
    @Mock private RdStockTransactionMapper stockTransactionMapper;
    @InjectMocks private RdPackageUnitServiceImpl service;

    @Test
    void shouldRequireReasonWhenReprintingLabel() {
        when(packageMapper.selectByIdForUpdate(1L)).thenReturn(new RdPackageUnitDO()
                .setId(1L).setStatus(RdPackageStatusEnum.LABELED.getStatus()));

        assertThrows(RuntimeException.class, () -> service.markPrinted(1L, 2L, "operator", null));
    }

    @Test
    void shouldLockProductionRowBeforeAllocatingPackageCodes() {
        RdProductionOutputDO production = new RdProductionOutputDO().setId(1L)
                .setProductionDate(LocalDate.of(2026, 7, 12)).setCustomerName("customer")
                .setProductModel("026").setFurnaceNo("1-01").setFurnaceLayer("X")
                .setOutputQty(BigDecimal.ONE).setPackageWeight(new BigDecimal("1000"))
                .setStatus(RdProductionOutputStatusEnum.CONFIRMED.getStatus());
        when(productionMapper.selectByIdForUpdate(1L)).thenReturn(production);
        when(packageMapper.selectCountByProductionOutputId(1L)).thenReturn(0L);
        when(packageMapper.selectCurrentDailySequenceForUpdate(1L, production.getProductionDate())).thenReturn(1);
        when(digitalLinkService.build("LD20260712A0001"))
                .thenReturn("https://id.tjldsic.com/pkg/LD20260712A0001");

        service.generate(1L, 1);

        verify(productionMapper).selectByIdForUpdate(1L);
        verify(packageMapper).incrementDailySequence(1L, production.getProductionDate(), 1);
        ArgumentCaptor<RdPackageUnitDO> captor = ArgumentCaptor.forClass(RdPackageUnitDO.class);
        verify(packageMapper).insert(captor.capture());
        assertEquals("LD20260712A0001", captor.getValue().getPackageCode());
        assertEquals("https://id.tjldsic.com/pkg/LD20260712A0001", captor.getValue().getQrCode());
    }

    @Test
    void shouldHideInternalFieldsFromPublicTrace() {
        RdPackageUnitDO unit = buildTracePackage();
        RdQualityInspectionDO inspection = buildInspection();
        RdStockTransactionDO transaction = buildInboundTransaction();
        when(packageMapper.selectByPackageCode(unit.getPackageCode())).thenReturn(unit);
        when(qualityInspectionMapper.selectList(any())).thenReturn(List.of(inspection));
        when(stockTransactionMapper.selectList(any())).thenReturn(List.of(transaction));
        when(digitalLinkService.build(unit.getPackageCode()))
                .thenReturn("https://id.tjldsic.com/pkg/" + unit.getPackageCode());

        RdPackageTraceRespVO result = service.getTraceDetail(unit.getPackageCode(), false);

        assertFalse(result.getInternalView());
        assertNull(result.getCustomerName());
        assertNull(result.getLocationCode());
        assertNull(result.getProductionOutputId());
        assertEquals("在库", result.getStatusName());
        assertEquals(4, result.getEvents().size());
        assertTrue(result.getEvents().stream().noneMatch(event ->
                event.getDetail().contains("仓管员张三") || event.getDetail().contains("A-01")));
    }

    @Test
    void shouldIncludeOperationalFieldsInInternalTrace() {
        RdPackageUnitDO unit = buildTracePackage();
        when(packageMapper.selectByPackageCode(unit.getPackageCode())).thenReturn(unit);
        when(qualityInspectionMapper.selectList(any())).thenReturn(List.of(buildInspection()));
        when(stockTransactionMapper.selectList(any())).thenReturn(List.of(buildInboundTransaction()));
        when(digitalLinkService.build(unit.getPackageCode()))
                .thenReturn("https://id.tjldsic.com/pkg/" + unit.getPackageCode());

        RdPackageTraceRespVO result = service.getTraceDetail(unit.getPackageCode(), true);

        assertTrue(result.getInternalView());
        assertEquals("示例客户", result.getCustomerName());
        assertEquals("A-01", result.getLocationCode());
        assertEquals(99L, result.getProductionOutputId());
        assertTrue(result.getEvents().stream().anyMatch(event ->
                event.getDetail().contains("仓管员张三") && event.getDetail().contains("A-01")));
    }

    private static RdPackageUnitDO buildTracePackage() {
        RdPackageUnitDO unit = new RdPackageUnitDO()
                .setId(10L)
                .setPackageCode("LD20260712A0001")
                .setProductionOutputId(99L)
                .setProductionDate(LocalDate.of(2026, 7, 12))
                .setCustomerName("示例客户")
                .setProductModel("YG15")
                .setFurnaceNo("1-13#28")
                .setFurnaceLayer("下层")
                .setPackageWeight(new BigDecimal("1000"))
                .setLocationCode("A-01")
                .setStatus(RdPackageStatusEnum.IN_STOCK.getStatus());
        unit.setCreateTime(LocalDateTime.of(2026, 7, 12, 8, 0));
        return unit;
    }

    private static RdQualityInspectionDO buildInspection() {
        RdQualityInspectionDO inspection = new RdQualityInspectionDO();
        inspection.setId(20L);
        inspection.setPackageId(10L);
        inspection.setStatus("REVIEWED");
        inspection.setConclusion("PASS");
        inspection.setSubmitterName("质检员李四");
        inspection.setSubmitTime(LocalDateTime.of(2026, 7, 12, 9, 0));
        inspection.setReviewerName("复核员王五");
        inspection.setReviewTime(LocalDateTime.of(2026, 7, 12, 9, 30));
        return inspection;
    }

    private static RdStockTransactionDO buildInboundTransaction() {
        RdStockTransactionDO transaction = RdStockTransactionDO.builder()
                .id(30L)
                .packageId(10L)
                .transactionType("INBOUND")
                .sourceNo("PUT-20260712-001")
                .operatorName("仓管员张三")
                .locationCode("A-01")
                .build();
        transaction.setCreateTime(LocalDateTime.of(2026, 7, 12, 10, 0));
        return transaction;
    }
}
