package com.ruiding.erp.service.stockmatrix;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.ruiding.erp.controller.admin.stockmatrix.vo.RdStockMatrixPageReqVO;
import com.ruiding.erp.controller.admin.stockmatrix.vo.RdStockMatrixPageRespVO;
import com.ruiding.erp.dal.dataobject.stockmatrix.RdStockMatrixAggregateDO;
import com.ruiding.erp.dal.dataobject.stockmatrix.RdStockMatrixDailyDO;
import com.ruiding.erp.dal.mysql.stockmatrix.RdStockMatrixMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RdStockMatrixServiceImplTest {

    @Mock
    private RdStockMatrixMapper mapper;
    @InjectMocks
    private RdStockMatrixServiceImpl service;

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldAcceptThirtyOneDaysAndRejectThirtyTwoDays() {
        RdStockMatrixPageReqVO valid = request(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        RdStockMatrixPageReqVO invalid = request(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1));

        assertTrue(valid.isDateRangeValid());
        assertFalse(invalid.isDateRangeValid());
    }

    @Test
    void shouldFillMissingDailyValuesAndReturnUnpagedTotals() {
        TenantContextHolder.setTenantId(1L);
        RdStockMatrixPageReqVO req = request(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2));
        RdStockMatrixAggregateDO row = aggregate(10L, "2", "5");
        RdStockMatrixAggregateDO totals = aggregate(null, "20", "50");
        RdStockMatrixDailyDO rowDaily = daily(10L, LocalDate.of(2026, 7, 2), "3");
        RdStockMatrixDailyDO totalDaily = daily(null, LocalDate.of(2026, 7, 2), "30");
        when(mapper.selectMatrixCount(1L, req)).thenReturn(1L);
        when(mapper.selectMatrixPage(eq(1L), eq(req), eq(0L), eq(20)))
                .thenReturn(Collections.singletonList(row));
        when(mapper.selectDailyMovements(eq(1L), eq(req.getStartDate()), eq(req.getEndDate()), any()))
                .thenReturn(Collections.singletonList(rowDaily));
        when(mapper.selectMatrixTotals(1L, req)).thenReturn(totals);
        when(mapper.selectDailyTotals(1L, req)).thenReturn(Collections.singletonList(totalDaily));

        RdStockMatrixPageRespVO result = service.getMatrixPage(req);

        assertEquals(2, result.getDates().size());
        assertDecimal("0", result.getList().get(0).getDailyMovements().get(0).getInboundQty());
        assertDecimal("3", result.getList().get(0).getDailyMovements().get(1).getInboundQty());
        assertDecimal("20", result.getTotals().getOpeningQty());
        assertDecimal("30", result.getTotals().getDailyMovements().get(1).getInboundQty());
    }

    private static RdStockMatrixPageReqVO request(LocalDate start, LocalDate end) {
        RdStockMatrixPageReqVO req = new RdStockMatrixPageReqVO();
        req.setPageNo(1);
        req.setPageSize(20);
        req.setStartDate(start);
        req.setEndDate(end);
        return req;
    }

    private static RdStockMatrixAggregateDO aggregate(Long lotId, String opening, String warehouse) {
        RdStockMatrixAggregateDO row = new RdStockMatrixAggregateDO();
        row.setStockLotId(lotId);
        row.setOpeningQty(decimal(opening));
        row.setWarehouseQty(decimal(warehouse));
        row.setPackageWeight(decimal("1000"));
        row.setStockWeight(decimal(warehouse).multiply(decimal("1000")));
        return row;
    }

    private static RdStockMatrixDailyDO daily(Long lotId, LocalDate date, String inbound) {
        RdStockMatrixDailyDO daily = new RdStockMatrixDailyDO();
        daily.setStockLotId(lotId);
        daily.setBizDate(date);
        daily.setInboundQty(decimal(inbound));
        return daily;
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, decimal(expected).compareTo(actual));
    }
}
