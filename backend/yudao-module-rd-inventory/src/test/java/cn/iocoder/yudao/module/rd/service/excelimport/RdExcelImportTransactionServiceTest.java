package cn.iocoder.yudao.module.rd.service.excelimport;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.rd.dal.dataobject.excelimport.RdExcelImportRowDO;
import cn.iocoder.yudao.module.rd.dal.dataobject.excelimport.RdExcelImportTaskDO;
import cn.iocoder.yudao.module.rd.dal.mysql.excelimport.RdExcelImportErrorMapper;
import cn.iocoder.yudao.module.rd.dal.mysql.excelimport.RdExcelImportRowMapper;
import cn.iocoder.yudao.module.rd.dal.mysql.excelimport.RdExcelImportTaskMapper;
import cn.iocoder.yudao.module.rd.enums.RdExcelImportTaskStatusEnum;
import cn.iocoder.yudao.module.rd.service.stock.RdStockLotService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RdExcelImportTransactionServiceTest {

    @Mock
    private RdExcelImportTaskMapper taskMapper;
    @Mock
    private RdExcelImportRowMapper rowMapper;
    @Mock
    private RdExcelImportErrorMapper errorMapper;
    @Mock
    private RdStockLotService stockLotService;
    @InjectMocks
    private RdExcelImportTransactionService service;

    @Test
    void shouldReturnPreviousResultWhenConfirmationIsRepeated() {
        RdExcelImportTaskDO task = task(1L, RdExcelImportTaskStatusEnum.SUCCESS.getStatus(), 0, 12);
        when(taskMapper.selectByIdForUpdate(1L)).thenReturn(task);

        assertEquals(12, service.confirm(1L, 9L, "admin"));
        verify(stockLotService, never()).createImportedOpeningStock(any(), any(), any(), any(), any());
    }

    @Test
    void shouldRejectConfirmationWhenPrecheckHasErrors() {
        RdExcelImportTaskDO task = task(2L, RdExcelImportTaskStatusEnum.PRECHECKED.getStatus(), 1, 0);
        when(taskMapper.selectByIdForUpdate(2L)).thenReturn(task);

        assertThrows(ServiceException.class, () -> service.confirm(2L, 9L, "admin"));
        verify(stockLotService, never()).createImportedOpeningStock(any(), any(), any(), any(), any());
    }

    @Test
    void shouldCreateTraceableOpeningStockAndMarkRowImported() {
        RdExcelImportTaskDO task = task(3L, RdExcelImportTaskStatusEnum.PRECHECKED.getStatus(), 0, 0);
        task.setValidRows(1).setBizDate(LocalDate.of(2026, 7, 8));
        RdExcelImportRowDO row = new RdExcelImportRowDO()
                .setId(31L).setTaskId(3L).setRowNo(85).setStatus("VALID")
                .setLocationCode("3-22").setCustomerName("贝特瑞产品一部")
                .setProductModel("13031").setFurnaceNo("1-13#27").setFurnaceLayer("下层【X】")
                .setWarehouseQty(decimal("2")).setPackageWeight(decimal("1000"));
        when(taskMapper.selectByIdForUpdate(3L)).thenReturn(task);
        when(rowMapper.selectValidRows(3L)).thenReturn(Collections.singletonList(row));
        when(stockLotService.createImportedOpeningStock(any(), eq(3L), eq(85), eq(9L), eq("admin")))
                .thenReturn(88L);

        assertEquals(1, service.confirm(3L, 9L, "admin"));
        assertEquals("IMPORTED", row.getStatus());
        assertEquals(88L, row.getStockLotId());
        assertEquals(RdExcelImportTaskStatusEnum.SUCCESS.getStatus(), task.getStatus());
        assertEquals(1, task.getImportedRows());
        verify(rowMapper).updateById(row);
    }

    private static RdExcelImportTaskDO task(Long id, String status, int failedRows, int importedRows) {
        return new RdExcelImportTaskDO().setId(id).setStatus(status)
                .setFailedRows(failedRows).setValidRows(1).setImportedRows(importedRows);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

}
