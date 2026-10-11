package com.ruiding.erp.service.packageunit;

import com.ruiding.erp.controller.admin.packageunit.vo.RdPackageTraceRespVO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.packageunit.RdLabelPrintLogDO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageTaskEventDO;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayScanRecordDO;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingScanRecordDO;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingExceptionDO;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.inOrder;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import com.ruiding.erp.enums.ErrorCodeConstants;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;

import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class RdPackageUnitServiceImplTest {

    @Mock private RdPackageUnitMapper packageMapper;
    @Mock private RdProductionOutputMapper productionMapper;
    @Mock private com.ruiding.erp.dal.mysql.production.RdProductionTaskMapper productionTaskMapper;
    @Mock private RdLabelPrintLogMapper printLogMapper;
    @Mock private RdDigitalLinkService digitalLinkService;
    @Mock private RdQualityInspectionMapper qualityInspectionMapper;
    @Mock private RdStockTransactionMapper stockTransactionMapper;
    @Mock private com.ruiding.erp.dal.mysql.packageunit.RdPackageTaskEventMapper taskEventMapper;
    @Mock private com.ruiding.erp.dal.mysql.putaway.RdPutawayScanRecordMapper putawayScanRecordMapper;
    @Mock private com.ruiding.erp.dal.mysql.loading.RdLoadingScanRecordMapper loadingScanRecordMapper;
    @Mock private com.ruiding.erp.dal.mysql.putaway.RdPutawayExceptionMapper putawayExceptionMapper;
    @Mock private com.ruiding.erp.dal.mysql.loading.RdLoadingExceptionMapper loadingExceptionMapper;
    @InjectMocks private RdPackageUnitServiceImpl service;

    @Test
    void shouldRequireReasonWhenReprintingLabel() {
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.markPrinted(List.of(1L), 2L, "operator", true, " "));
        assertEquals(ErrorCodeConstants.LABEL_REPRINT_REASON_REQUIRED.getCode(), error.getCode());
        verify(packageMapper, never()).selectByIdForUpdate(anyLong());
    }

    @Test
    void shouldPrintBatchWithOneLogPerPackageAndOrderedLocks() {
        for (Long id : List.of(1L, 2L)) {
            when(packageMapper.selectByIdForUpdate(id)).thenReturn(new RdPackageUnitDO()
                    .setId(id).setStatus("CREATED"));
        }

        service.markPrinted(List.of(2L, 1L), 3L, "operator", false, null);

        var locks = inOrder(packageMapper);
        locks.verify(packageMapper).selectByIdForUpdate(1L);
        locks.verify(packageMapper).selectByIdForUpdate(2L);
        ArgumentCaptor<RdPackageUnitDO> units = ArgumentCaptor.forClass(RdPackageUnitDO.class);
        verify(packageMapper, times(2)).updateById(units.capture());
        assertTrue(units.getAllValues().stream().allMatch(unit -> "LABELED".equals(unit.getStatus())));
        ArgumentCaptor<RdLabelPrintLogDO> logs = ArgumentCaptor.forClass(RdLabelPrintLogDO.class);
        verify(printLogMapper, times(2)).insert(logs.capture());
        assertEquals(List.of(1L, 2L), logs.getAllValues().stream().map(RdLabelPrintLogDO::getPackageId).toList());
        assertTrue(logs.getAllValues().stream().allMatch(log -> !log.getReprint() && log.getPrintSequence() == 1));
    }

    @Test
    void shouldRejectMixedStateBatchBeforeWritingAnyLog() {
        when(packageMapper.selectByIdForUpdate(1L)).thenReturn(new RdPackageUnitDO().setId(1L).setStatus("CREATED"));
        when(packageMapper.selectByIdForUpdate(2L)).thenReturn(new RdPackageUnitDO().setId(2L).setStatus("IN_STOCK"));

        assertThrows(ServiceException.class,
                () -> service.markPrinted(List.of(1L, 2L), 3L, "operator", false, null));

        verify(packageMapper, never()).updateById(any(RdPackageUnitDO.class));
        verify(printLogMapper, never()).insert(any(RdLabelPrintLogDO.class));
    }

    @Test
    void shouldPreserveInventoryAndIdentityWhenReprinting() {
        RdPackageUnitDO unit = buildTracePackage();
        when(packageMapper.selectByIdForUpdate(10L)).thenReturn(unit);
        when(printLogMapper.selectCount(org.mockito.ArgumentMatchers.<SFunction<RdLabelPrintLogDO, ?>>any(),
                eq(10L))).thenReturn(1L);

        service.markPrinted(List.of(10L), 3L, "operator", true, " damaged label ");

        assertEquals("IN_STOCK", unit.getStatus());
        assertEquals("A-01", unit.getLocationCode());
        assertEquals("LD20260712A0001", unit.getPackageCode());
        verify(packageMapper, never()).updateById(any(RdPackageUnitDO.class));
        ArgumentCaptor<RdLabelPrintLogDO> log = ArgumentCaptor.forClass(RdLabelPrintLogDO.class);
        verify(printLogMapper).insert(log.capture());
        assertTrue(log.getValue().getReprint());
        assertEquals(2, log.getValue().getPrintSequence());
        assertEquals("damaged label", log.getValue().getReprintReason());
    }

    @Test
    void shouldRejectDuplicateIdsAndVoidPackage() {
        assertThrows(ServiceException.class,
                () -> service.markPrinted(List.of(1L, 1L), 3L, "operator", false, null));
        verify(packageMapper, never()).selectByIdForUpdate(anyLong());
        when(packageMapper.selectByIdForUpdate(1L)).thenReturn(new RdPackageUnitDO().setId(1L).setStatus("VOIDED"));
        assertThrows(ServiceException.class,
                () -> service.markPrinted(List.of(1L), 3L, "operator", true, "damaged"));
        verify(printLogMapper, never()).insert(any(RdLabelPrintLogDO.class));
    }

    @Test
    void shouldLockProductionRowBeforeAllocatingPackageCodes() {
        RdProductionOutputDO production = new RdProductionOutputDO().setId(1L)
                .setProductionDate(LocalDate.of(2026, 7, 12)).setCustomerName("customer")
                .setProductModel("026").setFurnaceNo("1-01").setFurnaceLayer("X")
                .setOutputQty(BigDecimal.ONE).setPackageWeight(new BigDecimal("1000"))
                .setStatus(RdProductionOutputStatusEnum.CONFIRMED.getStatus());
        when(productionMapper.selectByIdForUpdate(1L)).thenReturn(production);
        when(productionMapper.selectById(1L)).thenReturn(production);
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

    @Test void taskPrintingMustRejectForeignPackageBeforeRegisteringAnyLabel() {
        when(productionTaskMapper.selectByIdForUpdate(10L)).thenReturn(new com.ruiding.erp.dal.dataobject.production.RdProductionTaskDO().setId(10L));
        when(packageMapper.selectById(1L)).thenReturn(new RdPackageUnitDO().setId(1L).setProductionTaskId(10L));
        when(packageMapper.selectById(2L)).thenReturn(new RdPackageUnitDO().setId(2L).setProductionTaskId(11L));
        assertThrows(RuntimeException.class, () -> service.markTaskPrinted(10L, List.of(1L,2L), 3L, "operator", false, null));
        verify(packageMapper, never()).selectByIdForUpdate(anyLong());
        verifyNoInteractions(printLogMapper);
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
        assertNull(result.getProductionTaskId());
        assertNull(result.getProductionTaskNo());
        assertNull(result.getQualityInspectionId());
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
        assertEquals(20L, result.getQualityInspectionId());
        assertTrue(result.getEvents().stream().anyMatch(event ->
                event.getDetail().contains("仓管员张三") && event.getDetail().contains("A-01")));
    }

    private static RdPackageUnitDO buildTracePackage() {
        RdPackageUnitDO unit = new RdPackageUnitDO()
                .setId(10L)
                .setPackageCode("LD20260712A0001")
                .setProductionOutputId(99L)
                .setProductionTaskId(100L).setProductionTaskNo("SC-PRIVATE-TASK")
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

    @Test
    void shouldSanitizeLabelHistoryAndNeverReadPrivateTaskStoresForPublicView() {
        RdPackageUnitDO unit = buildTracePackage();
        when(packageMapper.selectByPackageCode(unit.getPackageCode())).thenReturn(unit);
        when(printLogMapper.selectList(any())).thenReturn(List.of(new RdLabelPrintLogDO()
                .setId(1L).setReprint(true).setPrintSequence(2).setPrinterName("private operator")
                .setReprintReason("private reason").setPrintTime(unit.getCreateTime().plusMinutes(1))));
        RdQualityInspectionDO inspection = buildInspection();
        inspection.setConclusion("PASSED");
        when(qualityInspectionMapper.selectList(any())).thenReturn(List.of(inspection));

        var result = service.getTraceDetail(unit.getPackageCode(), false);

        assertTrue(result.getEvents().stream().anyMatch(event -> "质检通过".equals(event.getTitle())));
        assertTrue(result.getEvents().stream().anyMatch(event -> "LABEL".equals(event.getStage())));
        assertFalse(result.getEvents().toString().contains("private"));
        verify(taskEventMapper, never()).selectByPackageId(anyLong());
        verify(putawayScanRecordMapper, never()).selectList(any());
        verify(loadingScanRecordMapper, never()).selectList(any());
        verify(putawayExceptionMapper, never()).selectList(any());
        verify(loadingExceptionMapper, never()).selectList(any());
    }

    @Test
    void shouldMergeRealTimesAndDeduplicateBatchHeaderAndExceptionSnapshots() {
        RdPackageUnitDO unit = buildTracePackage();
        LocalDateTime time = unit.getCreateTime();
        when(packageMapper.selectByPackageCode(unit.getPackageCode())).thenReturn(unit);
        when(printLogMapper.selectList(any())).thenReturn(List.of(new RdLabelPrintLogDO()
                .setId(1L).setPrintSequence(1).setReprint(false).setPrintTime(time.plusMinutes(1))));
        RdPackageTaskEventDO loading = new RdPackageTaskEventDO().setId(1L).setPackageId(10L)
                .setEventCode("SCAN_CONFIRMED").setStage("LOADING").setSourceType("LOADING_SCAN")
                .setSourceId(80L).setSourceNo("LC-01").setSourceVersion(1)
                .setLocationCode("A-01").setOperatorName("keeper").setEventTime(time.plusMinutes(5));
        RdPackageTaskEventDO reported = new RdPackageTaskEventDO().setId(2L).setPackageId(10L)
                .setEventCode("EXCEPTION_REPORTED").setStage("EXCEPTION").setSourceType("LOADING_EXCEPTION")
                .setSourceId(81L).setSourceNo("EX-01").setSourceVersion(0)
                .setDetail("exception detail").setEventTime(time.plusMinutes(3));
        when(taskEventMapper.selectByPackageId(10L)).thenReturn(List.of(loading, reported));
        when(putawayScanRecordMapper.selectList(any())).thenReturn(List.of(new RdPutawayScanRecordDO()
                .setId(70L).setTaskId(60L).setPackageId(10L).setOperatorName("keeper")
                .setScanTime(time.plusMinutes(2))));
        RdLoadingScanRecordDO header = new RdLoadingScanRecordDO();
        header.setId(80L);
        header.setTaskId(61L);
        header.setScanTime(time.plusMinutes(5));
        when(loadingScanRecordMapper.selectList(any())).thenReturn(List.of(header));
        RdLoadingExceptionDO exception = new RdLoadingExceptionDO();
        exception.setId(81L);
        exception.setReportedTime(time.plusMinutes(3));
        exception.setHandleTime(time.plusMinutes(4));
        exception.setHandlingResult("legacy resolution");
        when(loadingExceptionMapper.selectList(any())).thenReturn(List.of(exception));
        RdStockTransactionDO inbound = buildInboundTransaction();
        inbound.setCreateTime(time.plusMinutes(6));
        when(stockTransactionMapper.selectList(any())).thenReturn(List.of(inbound));

        var result = service.getTraceDetail(unit.getPackageCode(), true);

        assertEquals(List.of("PRODUCTION", "LABEL", "PUTAWAY", "EXCEPTION", "EXCEPTION", "LOADING", "STOCK"),
                result.getEvents().stream().map(event -> event.getStage()).toList());
        assertEquals(1L, result.getEvents().stream().filter(event -> "LOADING".equals(event.getStage())).count());
        assertTrue(result.getEvents().get(5).getDetail().contains("LC-01"));
        assertTrue(result.getEvents().get(5).getDetail().contains("A-01"));
        assertTrue(result.getEvents().get(4).getDetail().contains("legacy resolution"));
    }

    @Test
    void shouldKeepReplacementHistoryOnOriginalPackageWithoutReadingMutableTaskLines() {
        RdPackageUnitDO unit = buildTracePackage();
        when(packageMapper.selectByPackageCode(unit.getPackageCode())).thenReturn(unit);
        when(taskEventMapper.selectByPackageId(10L)).thenReturn(List.of(new RdPackageTaskEventDO()
                .setPackageId(10L).setPackageCode(unit.getPackageCode()).setEventCode("REPLACED_OUT")
                .setStage("LOADING").setSourceType("LOADING_TASK").setSourceId(1L).setSourceNo("LC-01")
                .setSourceVersion(4).setRelatedPackageCode("NEW-PACKAGE").setLocationCode("OLD-LOCATION")
                .setEventTime(unit.getCreateTime().plusMinutes(1))));

        var result = service.getTraceDetail(unit.getPackageCode(), true);

        assertEquals(2, result.getEvents().size());
        assertEquals("包件已从装车任务换出", result.getEvents().get(1).getTitle());
        assertTrue(result.getEvents().get(1).getDetail().contains("OLD-LOCATION"));
        assertTrue(result.getEvents().get(1).getDetail().contains("NEW-PACKAGE"));
        assertFalse(result.getEvents().stream().anyMatch(event -> "仓管已核验装车包件".equals(event.getTitle())));
    }

    @Test
    void shouldNotInventMilestonesWhenEventTimesAreMissing() {
        RdPackageUnitDO unit = buildTracePackage();
        unit.setCreateTime(null);
        when(packageMapper.selectByPackageCode(unit.getPackageCode())).thenReturn(unit);
        when(printLogMapper.selectList(any())).thenReturn(List.of(new RdLabelPrintLogDO().setPrintSequence(1)));

        assertTrue(service.getTraceDetail(unit.getPackageCode(), true).getEvents().isEmpty());
    }

    @Test
    void shouldNotTreatUnselectedRepresentativeAsConfirmedPackage() {
        RdPackageUnitDO unit = buildTracePackage();
        when(packageMapper.selectByPackageCode(unit.getPackageCode())).thenReturn(unit);
        RdLoadingScanRecordDO header = new RdLoadingScanRecordDO();
        header.setId(80L);
        header.setPackageId(unit.getId());
        header.setScanTime(unit.getCreateTime().plusMinutes(1));
        when(loadingScanRecordMapper.selectList(any())).thenReturn(List.of(header));
        when(taskEventMapper.hasSourceSnapshots("LOADING_SCAN", 80L)).thenReturn(true);

        var result = service.getTraceDetail(unit.getPackageCode(), true);

        assertEquals(1, result.getEvents().size());
        assertEquals("PRODUCTION", result.getEvents().get(0).getStage());
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
