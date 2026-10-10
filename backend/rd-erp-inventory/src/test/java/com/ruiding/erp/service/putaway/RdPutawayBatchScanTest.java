package com.ruiding.erp.service.putaway;

import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import com.ruiding.erp.controller.admin.putaway.vo.RdPutawayBatchScanConfirmReqVO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.putaway.*;
import com.ruiding.erp.dal.mysql.putaway.*;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.dal.mysql.stock.RdStockLotMapper;
import com.ruiding.erp.dal.mysql.stock.RdStockTransactionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RdPutawayBatchScanTest {
    @Mock private RdPutawayTaskMapper taskMapper;
    @Mock private RdPutawayTaskLineMapper taskLineMapper;
    @Mock private RdPutawayScanRecordMapper scanRecordMapper;
    @Mock private RdPutawayBatchScanRecordMapper batchScanRecordMapper;
    @Mock private RdPutawayExceptionMapper exceptionMapper;
    @Mock private RdPackageUnitMapper packageMapper;
    @Mock private PermissionService permissionService;
    @Mock private RdStockLotMapper stockLotMapper;
    @Mock private RdStockTransactionMapper stockTransactionMapper;
    @InjectMocks private RdPutawayTaskServiceImpl service;

    @Test
    void shouldVerifyTwoPackagesWithOneHeaderAndNoStockPosting() {
        Fixture fixture = setupTwoPackages();
        var result = service.confirmBatchScan(request(List.of(1L, 2L)), 20L, "仓管20");
        assertEquals(2, result.getConfirmedCount());
        assertEquals(2, result.getTask().getScannedPackageCount());
        assertEquals("PENDING_CONFIRMATION", result.getTask().getStatus());
        assertTrue(result.getLines().stream().allMatch(line -> "SCANNED".equals(line.getLineStatus())));
        assertEquals("INBOUND_PENDING", fixture.first.getStatus());
        assertEquals("INBOUND_PENDING", fixture.second.getStatus());
        verify(scanRecordMapper, times(2)).insert(any(RdPutawayScanRecordDO.class));
        verify(batchScanRecordMapper).insert(any(RdPutawayBatchScanRecordDO.class));
        verifyNoInteractions(stockLotMapper, stockTransactionMapper);
    }

    @Test
    void shouldAcceptPartialSelectionAndKeepRemainingPackageUnverified() {
        Fixture fixture = setupTwoPackages();
        var result = service.confirmBatchScan(request(List.of(1L)), 20L, "仓管20");
        assertEquals(1, result.getConfirmedCount());
        assertEquals("MOVING", result.getTask().getStatus());
        assertEquals("PENDING", fixture.lines.get(1).getLineStatus());
        assertEquals(0, fixture.lines.get(1).getScanCount());
        verify(scanRecordMapper).insert(any(RdPutawayScanRecordDO.class));
        verifyNoInteractions(stockLotMapper, stockTransactionMapper);
    }

    @Test
    void shouldReplayOriginalBatchAndRejectChangedSelection() {
        setupTwoPackages();
        AtomicReference<RdPutawayBatchScanRecordDO> saved = new AtomicReference<>();
        when(batchScanRecordMapper.selectByTaskAndKey(50L, "batch-one")).thenAnswer(inv -> saved.get());
        doAnswer(inv -> { saved.set(inv.getArgument(0)); saved.get().setId(90L); return 1; })
                .when(batchScanRecordMapper).insert(any(RdPutawayBatchScanRecordDO.class));
        service.confirmBatchScan(request(List.of(1L, 2L)), 20L, "仓管20");
        var replay = service.confirmBatchScan(request(List.of(2L, 1L)), 20L, "仓管20");
        assertTrue(replay.getReplayed());
        assertEquals(0, replay.getConfirmedCount());
        assertEquals(90L, replay.getRecordId());
        assertThrows(RuntimeException.class, () -> service.confirmBatchScan(request(List.of(1L)), 20L, "仓管20"));
        verify(scanRecordMapper, times(2)).insert(any(RdPutawayScanRecordDO.class));
        verify(batchScanRecordMapper).insert(any(RdPutawayBatchScanRecordDO.class));
    }

    @Test
    void shouldRejectInvalidSiblingBeforeWritingAnyScan() {
        Fixture fixture = setupTwoPackages();
        fixture.second.setStatus("QC_REJECTED");
        assertThrows(RuntimeException.class, () -> service.confirmBatchScan(request(List.of(1L, 2L)), 20L, "仓管20"));
        assertEquals("ASSIGNED", fixture.task.getStatus());
        verify(scanRecordMapper, never()).insert(any(RdPutawayScanRecordDO.class));
        verify(batchScanRecordMapper, never()).insert(any(RdPutawayBatchScanRecordDO.class));
    }

    @Test
    void shouldRejectStalePlanBeforeReadingOrWritingLines() {
        when(permissionService.hasAnyPermissions(20L, "rd:putaway-task:confirm")).thenReturn(true);
        when(taskMapper.selectByIdForUpdate(50L)).thenReturn(new RdPutawayTaskDO().setId(50L).setVersion(1).setStatus("ASSIGNED"));
        assertThrows(RuntimeException.class, () -> service.confirmBatchScan(request(List.of(1L)), 20L, "仓管20"));
        verifyNoInteractions(taskLineMapper, scanRecordMapper, packageMapper);
    }

    @Test
    void shouldRejectDriverBatchScanWithoutTouchingTask() {
        assertThrows(RuntimeException.class, () -> service.confirmBatchScan(request(List.of(1L)), 30L, "司机30"));
        verifyNoInteractions(taskMapper, taskLineMapper, scanRecordMapper, batchScanRecordMapper);
    }

    private Fixture setupTwoPackages() {
        when(permissionService.hasAnyPermissions(20L, "rd:putaway-task:confirm")).thenReturn(true);
        var task = new RdPutawayTaskDO().setId(50L).setTaskNo("PUT-LOCAL").setVersion(0)
                .setForkliftDriverId(30L).setStatus("ASSIGNED").setPackageCount(2).setScannedPackageCount(0);
        var firstLine = line(1L, 11L, "PK1");
        var secondLine = line(2L, 12L, "PK2");
        var lines = List.of(firstLine, secondLine);
        when(taskMapper.selectByIdForUpdate(50L)).thenReturn(task);
        lenient().when(taskMapper.selectById(50L)).thenReturn(task);
        when(taskLineMapper.selectListByTaskIdForUpdate(50L)).thenReturn(lines);
        lenient().when(taskLineMapper.selectListByTaskId(50L)).thenReturn(lines);
        var first = unit(firstLine);
        var second = unit(secondLine);
        when(packageMapper.selectByIdForUpdate(11L)).thenReturn(first);
        lenient().when(packageMapper.selectByIdForUpdate(12L)).thenReturn(second);
        return new Fixture(task, lines, first, second);
    }

    private static RdPutawayBatchScanConfirmReqVO request(List<Long> ids) {
        var request = new RdPutawayBatchScanConfirmReqVO();
        request.setId(50L); request.setVersion(0); request.setPackageCode("PK1");
        request.setIdempotencyKey("batch-one"); request.setTaskLineIds(ids);
        return request;
    }

    private static RdPutawayTaskLineDO line(Long id, Long packageId, String code) {
        return new RdPutawayTaskLineDO().setId(id).setTaskId(50L).setPackageId(packageId).setPackageCode(code)
                .setCustomerName("local").setProductModel("local").setFurnaceNo("local").setFurnaceLayer("上层")
                .setPackageWeight(new BigDecimal("1000")).setScanCount(0).setVersion(0).setLineStatus("PENDING");
    }

    private static RdPackageUnitDO unit(RdPutawayTaskLineDO line) {
        return new RdPackageUnitDO().setId(line.getPackageId()).setPackageCode(line.getPackageCode())
                .setCustomerName(line.getCustomerName()).setProductModel(line.getProductModel())
                .setFurnaceNo(line.getFurnaceNo()).setFurnaceLayer(line.getFurnaceLayer())
                .setPackageWeight(line.getPackageWeight()).setStatus("INBOUND_PENDING");
    }

    private record Fixture(RdPutawayTaskDO task, List<RdPutawayTaskLineDO> lines,
                           RdPackageUnitDO first, RdPackageUnitDO second) {}
}
