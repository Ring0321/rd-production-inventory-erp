package com.ruiding.erp.service.stockmatrix;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.ruiding.erp.controller.admin.stockmatrix.vo.RdStockMatrixDailyRespVO;
import com.ruiding.erp.controller.admin.stockmatrix.vo.RdStockMatrixPageReqVO;
import com.ruiding.erp.controller.admin.stockmatrix.vo.RdStockMatrixPageRespVO;
import com.ruiding.erp.controller.admin.stockmatrix.vo.RdStockMatrixRowRespVO;
import com.ruiding.erp.dal.dataobject.stockmatrix.RdStockMatrixAggregateDO;
import com.ruiding.erp.dal.dataobject.stockmatrix.RdStockMatrixDailyDO;
import com.ruiding.erp.dal.mysql.stockmatrix.RdStockMatrixMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RdStockMatrixServiceImpl implements RdStockMatrixService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @Resource
    private RdStockMatrixMapper mapper;

    @Override
    public RdStockMatrixPageRespVO getMatrixPage(RdStockMatrixPageReqVO reqVO) {
        validateRange(reqVO.getStartDate(), reqVO.getEndDate());
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        long total = mapper.selectMatrixCount(tenantId, reqVO);
        List<RdStockMatrixAggregateDO> aggregates = total == 0 ? Collections.emptyList()
                : mapper.selectMatrixPage(tenantId, reqVO,
                (long) (reqVO.getPageNo() - 1) * reqVO.getPageSize(), reqVO.getPageSize());

        Map<Long, Map<LocalDate, RdStockMatrixDailyDO>> dailyByLot = loadDailyMovements(
                tenantId, reqVO, aggregates);
        List<RdStockMatrixRowRespVO> rows = aggregates.stream()
                .map(row -> convertRow(row, buildDates(reqVO), dailyByLot.get(row.getStockLotId())))
                .collect(Collectors.toList());
        RdStockMatrixAggregateDO totals = mapper.selectMatrixTotals(tenantId, reqVO);
        Map<LocalDate, RdStockMatrixDailyDO> dailyTotals = mapper.selectDailyTotals(tenantId, reqVO).stream()
                .collect(Collectors.toMap(RdStockMatrixDailyDO::getBizDate, item -> item));
        return new RdStockMatrixPageRespVO()
                .setDates(buildDates(reqVO))
                .setList(rows)
                .setTotal(total)
                .setTotals(totals == null ? emptyTotals(buildDates(reqVO))
                        : convertTotals(totals, buildDates(reqVO), dailyTotals));
    }

    private Map<Long, Map<LocalDate, RdStockMatrixDailyDO>> loadDailyMovements(
            Long tenantId, RdStockMatrixPageReqVO reqVO, List<RdStockMatrixAggregateDO> rows) {
        if (rows.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> lotIds = rows.stream().map(RdStockMatrixAggregateDO::getStockLotId).collect(Collectors.toList());
        Map<Long, Map<LocalDate, RdStockMatrixDailyDO>> result = new HashMap<>();
        for (RdStockMatrixDailyDO daily : mapper.selectDailyMovements(
                tenantId, reqVO.getStartDate(), reqVO.getEndDate(), lotIds)) {
            result.computeIfAbsent(daily.getStockLotId(), ignored -> new HashMap<>())
                    .put(daily.getBizDate(), daily);
        }
        return result;
    }

    private static RdStockMatrixRowRespVO convertRow(RdStockMatrixAggregateDO row, List<LocalDate> dates,
                                                      Map<LocalDate, RdStockMatrixDailyDO> dailyMap) {
        List<RdStockMatrixDailyRespVO> daily = dates.stream()
                .map(date -> convertDaily(date, dailyMap == null ? null : dailyMap.get(date)))
                .collect(Collectors.toList());
        return new RdStockMatrixRowRespVO()
                .setStockLotId(row.getStockLotId())
                .setLocationCode(row.getLocationCode())
                .setCustomerName(row.getCustomerName())
                .setProductModel(row.getProductModel())
                .setFurnaceNo(row.getFurnaceNo())
                .setFurnaceLayer(row.getFurnaceLayer())
                .setOpeningQty(value(row.getOpeningQty()))
                .setTransferQty(value(row.getTransferQty()))
                .setInboundQty(value(row.getInboundQty()))
                .setOutboundQty(value(row.getOutboundQty()))
                .setReturnQty(value(row.getReturnQty()))
                .setWarehouseQty(value(row.getWarehouseQty()))
                .setPackageWeight(value(row.getPackageWeight()))
                .setStockWeight(value(row.getStockWeight()))
                .setDailyMovements(daily);
    }

    private static RdStockMatrixDailyRespVO convertDaily(LocalDate date, RdStockMatrixDailyDO daily) {
        return new RdStockMatrixDailyRespVO().setBizDate(date)
                .setInboundQty(daily == null ? ZERO : value(daily.getInboundQty()))
                .setOutboundQty(daily == null ? ZERO : value(daily.getOutboundQty()))
                .setTransferQty(daily == null ? ZERO : value(daily.getTransferQty()))
                .setReturnQty(daily == null ? ZERO : value(daily.getReturnQty()));
    }

    private static RdStockMatrixRowRespVO convertTotals(RdStockMatrixAggregateDO totals,
                                                        List<LocalDate> dates,
                                                        Map<LocalDate, RdStockMatrixDailyDO> dailyTotals) {
        return new RdStockMatrixRowRespVO()
                .setOpeningQty(value(totals.getOpeningQty()))
                .setTransferQty(value(totals.getTransferQty()))
                .setInboundQty(value(totals.getInboundQty()))
                .setOutboundQty(value(totals.getOutboundQty()))
                .setReturnQty(value(totals.getReturnQty()))
                .setWarehouseQty(value(totals.getWarehouseQty()))
                .setStockWeight(value(totals.getStockWeight()))
                .setDailyMovements(dates.stream()
                        .map(date -> convertDaily(date, dailyTotals.get(date)))
                        .collect(Collectors.toList()));
    }

    private static RdStockMatrixRowRespVO emptyTotals(List<LocalDate> dates) {
        return convertTotals(new RdStockMatrixAggregateDO(), dates, Collections.emptyMap());
    }

    private static List<LocalDate> buildDates(RdStockMatrixPageReqVO reqVO) {
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = reqVO.getStartDate(); !date.isAfter(reqVO.getEndDate()); date = date.plusDays(1)) {
            dates.add(date);
        }
        return dates;
    }

    private static void validateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)
                || ChronoUnit.DAYS.between(startDate, endDate) > 30) {
            throw new IllegalArgumentException("date range must be between 1 and 31 days");
        }
    }

    private static BigDecimal value(BigDecimal value) {
        return value == null ? ZERO : value;
    }
}
