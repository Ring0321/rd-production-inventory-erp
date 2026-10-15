package com.ruiding.erp.service.weight;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingTaskDO;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingTaskLineDO;
import com.ruiding.erp.dal.dataobject.loading.RdPickupSlipDO;
import com.ruiding.erp.dal.dataobject.loading.RdPickupSlipLineDO;
import com.ruiding.erp.dal.dataobject.masterdata.RdWarehouseLocationDO;
import com.ruiding.erp.dal.dataobject.order.RdOrderFulfillmentDO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockLotDO;
import com.ruiding.erp.dal.mysql.loading.*;
import com.ruiding.erp.dal.mysql.masterdata.RdWarehouseLocationMapper;
import com.ruiding.erp.dal.mysql.order.RdOrderFulfillmentMapper;
import com.ruiding.erp.dal.mysql.order.RdSaleOrderMapper;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.dal.mysql.quality.RdQualityInspectionMapper;
import com.ruiding.erp.dal.mysql.stock.RdStockLotMapper;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class RdWeightInventoryReader {
    public record Inventory(List<RdWeightSolver.Bag> bags, Map<Long, Integer> lotCaps, int scannedCount, boolean complete) { }
    @Resource private RdPickupSlipMapper slipMapper;
    @Resource private RdPickupSlipLineMapper slipLineMapper;
    @Resource private RdLoadingTaskMapper taskMapper;
    @Resource private RdPackageUnitMapper packageMapper;
    @Resource private RdStockLotMapper stockLotMapper;
    @Resource private RdWarehouseLocationMapper locationMapper;
    @Resource private RdLoadingPackageClaimMapper claimMapper;
    @Resource private RdQualityInspectionMapper qualityMapper;
    @Resource private RdSaleOrderMapper orderMapper;
    @Resource private RdOrderFulfillmentMapper fulfillmentMapper;
    @Resource private RdLoadingTaskLineMapper taskLineMapper;

    public RdPickupSlipDO readSlip(Long id) { return slipMapper.selectById(id); }
    public List<RdPickupSlipLineDO> readLines(Long id) { return slipLineMapper.selectListBySlipId(id); }
    public boolean hasTask(Long slipId) {
        return taskMapper.selectCount(new LambdaQueryWrapperX<RdLoadingTaskDO>()
                .eq(RdLoadingTaskDO::getPickupSlipId, slipId)
                .and(item -> item.ne(RdLoadingTaskDO::getStatus, "CANCELLED").or().isNull(RdLoadingTaskDO::getStatus))) > 0;
    }

    public Inventory readCandidates(RdPickupSlipDO slip, RdPickupSlipLineDO line) {
        long started = System.nanoTime();
        var query = new LambdaQueryWrapperX<RdPackageUnitDO>()
                .eq(RdPackageUnitDO::getTenantId, slip.getTenantId())
                .eq(RdPackageUnitDO::getStatus, "IN_STOCK")
                .eq(RdPackageUnitDO::getCustomerName, slip.getCustomerName())
                .eq(RdPackageUnitDO::getProductModel, line.getProductModel())
                .eqIfPresent(RdPackageUnitDO::getFurnaceNo, line.getFurnaceNo())
                .eqIfPresent(RdPackageUnitDO::getFurnaceLayer, line.getFurnaceLayer());
        query.notExists("SELECT 1 FROM rd_loading_package_claim c WHERE c.package_id = rd_package_unit.id "
                + "AND c.tenant_id = rd_package_unit.tenant_id");
        query.orderByAsc(RdPackageUnitDO::getId).last("LIMIT 501");
        List<RdPackageUnitDO> units = packageMapper.selectList(query);
        if (units.size() > 500) return new Inventory(List.of(), Map.of(), units.size(), false);
        if (line.getSaleOrderId() != null) {
            var order = orderMapper.selectById(line.getSaleOrderId());
            if (order == null || !Objects.equals(order.getTenantId(), slip.getTenantId()) ||
                    !Objects.equals(order.getCustomerName(), slip.getCustomerName()) ||
                    !Objects.equals(order.getProductModel(), line.getProductModel()) ||
                    !("OPEN".equals(order.getStatus()) || "PARTIAL".equals(order.getStatus())))
                return new Inventory(List.of(), Map.of(), units.size(), true);
        }
        var bags = new ArrayList<RdWeightSolver.Bag>();
        Map<Long, RdStockLotDO> lots = new HashMap<>();
        Map<Long, RdWarehouseLocationDO> locations = new HashMap<>();
        Map<Long, Integer> caps = new HashMap<>();
        var ids = new HashSet<Long>();
        var codes = new HashSet<String>();
        for (var unit : units) {
            if ((System.nanoTime() - started) / 1_000_000 > 3000)
                return new Inventory(List.of(), Map.of(), units.size(), false);
            if (!validUnit(unit, slip, line) || claimMapper.isClaimed(slip.getTenantId(), unit.getId())) continue;
            if (!ids.add(unit.getId()) || !codes.add(unit.getPackageCode()))
                throw new IllegalArgumentException("Duplicate authoritative package identity");
            var lot = lots.computeIfAbsent(unit.getStockLotId(), stockLotMapper::selectById);
            var location = locations.computeIfAbsent(unit.getLocationId(), locationMapper::selectById);
            if (!validLotAndLocation(unit, lot, location, slip.getTenantId()) || !qualityPassed(unit, slip.getTenantId())) continue;
            int capacity = caps.computeIfAbsent(lot.getId(), ignored -> lotCapacity(lot, line));
            if (capacity < 1) continue;
            bags.add(new RdWeightSolver.Bag(unit.getId(), unit.getPackageCode(), unit.getPackageWeight(),
                    unit.getLocationId(), location.getLocationCode(), unit.getStockLotId()));
        }
        if ((System.nanoTime() - started) / 1_000_000 > 3000)
            return new Inventory(List.of(), Map.of(), units.size(), false);
        return new Inventory(List.copyOf(bags), Map.copyOf(caps), units.size(), true);
    }

    private boolean qualityPassed(RdPackageUnitDO unit, Long tenant) {
        var inspections = qualityMapper.selectListByCoveredPackageId(unit.getId());
        if (inspections.isEmpty() || inspections.stream().anyMatch(item -> item.getId() == null ||
                !Objects.equals(item.getTenantId(), tenant) ||
                !("PASSED".equals(item.getStatus()) || "REJECTED".equals(item.getStatus())))) return false;
        var latest = inspections.stream().max(Comparator.comparing(RdQualityInspectionDO::getId)).orElseThrow();
        return "PASSED".equals(latest.getStatus()) && "PASSED".equals(latest.getConclusion()) &&
                latest.getReviewerId() != null && latest.getReviewTime() != null && unit.getProductionOutputId() != null &&
                Objects.equals(latest.getProductionOutputId(), unit.getProductionOutputId());
    }

    private int lotCapacity(RdStockLotDO lot, RdPickupSlipLineDO line) {
        if (!quantity(lot.getWarehouseQty()) || !quantity(lot.getLockedQty()) ||
                lot.getLockedQty().compareTo(lot.getWarehouseQty()) > 0) return 0;
        Long claimed = packageMapper.selectCount(new LambdaQueryWrapperX<RdPackageUnitDO>()
                .eq(RdPackageUnitDO::getTenantId, lot.getTenantId()).eq(RdPackageUnitDO::getStockLotId, lot.getId())
                .exists("SELECT 1 FROM rd_loading_package_claim c WHERE c.package_id = rd_package_unit.id "
                        + "AND c.tenant_id = rd_package_unit.tenant_id"));
        Long allocatedClaims = packageMapper.selectCount(new LambdaQueryWrapperX<RdPackageUnitDO>()
                .eq(RdPackageUnitDO::getTenantId, lot.getTenantId()).eq(RdPackageUnitDO::getStockLotId, lot.getId())
                .exists("SELECT 1 FROM rd_loading_package_claim c "
                        + "JOIN rd_loading_task_line t ON t.id = c.task_line_id AND t.task_id = c.task_id "
                        + "AND t.package_id = c.package_id AND t.tenant_id = c.tenant_id AND t.deleted = 0 "
                        + "JOIN rd_order_fulfillment f ON f.id = t.fulfillment_id AND f.order_id = t.sale_order_id "
                        + "AND f.stock_lot_id = t.stock_lot_id AND f.tenant_id = c.tenant_id AND f.deleted = 0 "
                        + "AND f.status = 'ALLOCATED' AND f.allocated_qty > f.outbound_qty "
                        + "WHERE c.package_id = rd_package_unit.id AND c.tenant_id = rd_package_unit.tenant_id "
                        + "AND f.stock_lot_id = rd_package_unit.stock_lot_id"));
        if (allocatedClaims > claimed || BigDecimal.valueOf(allocatedClaims).compareTo(lot.getLockedQty()) > 0) return 0;
        BigDecimal physical = lot.getWarehouseQty().subtract(BigDecimal.valueOf(claimed));
        // Allocation-backed claims are already inside lockedQty; only unbacked claims consume free stock again.
        BigDecimal free = lot.getWarehouseQty().subtract(lot.getLockedQty())
                .subtract(BigDecimal.valueOf(claimed - allocatedClaims));
        if (line.getSaleOrderId() == null)
            return cap(physical.min(free));
        RdOrderFulfillmentDO allocation = fulfillmentMapper.selectActive(line.getSaleOrderId(), lot.getId());
        if (allocation == null || allocation.getId() == null || !Objects.equals(allocation.getTenantId(), lot.getTenantId()) ||
                !Objects.equals(allocation.getOrderId(), line.getSaleOrderId()) || !Objects.equals(allocation.getStockLotId(), lot.getId()) ||
                !"ALLOCATED".equals(allocation.getStatus()) || !quantity(allocation.getAllocatedQty()) ||
                !quantity(allocation.getOutboundQty())) return 0;
        BigDecimal remaining = allocation.getAllocatedQty().subtract(allocation.getOutboundQty());
        if (remaining.compareTo(lot.getLockedQty()) > 0) return 0;
        Long reserved = taskLineMapper.selectCount(new LambdaQueryWrapperX<RdLoadingTaskLineDO>()
                .eq(RdLoadingTaskLineDO::getTenantId, lot.getTenantId())
                .eq(RdLoadingTaskLineDO::getFulfillmentId, allocation.getId())
                .exists("SELECT 1 FROM rd_loading_package_claim c WHERE c.task_line_id = rd_loading_task_line.id "
                        + "AND c.tenant_id = rd_loading_task_line.tenant_id"));
        BigDecimal allocated = remaining.subtract(BigDecimal.valueOf(reserved));
        return cap(physical.min(allocated).min(free.add(allocated)));
    }

    private static boolean validUnit(RdPackageUnitDO unit, RdPickupSlipDO slip, RdPickupSlipLineDO line) {
        if (unit.getId() == null || unit.getId() <= 0 || unit.getPackageCode() == null || unit.getPackageCode().isBlank() ||
                !Objects.equals(unit.getTenantId(), slip.getTenantId()) || !"IN_STOCK".equals(unit.getStatus()) ||
                !Objects.equals(unit.getCustomerName(), slip.getCustomerName()) || !Objects.equals(unit.getProductModel(), line.getProductModel()) ||
                line.getFurnaceNo() != null && !Objects.equals(unit.getFurnaceNo(), line.getFurnaceNo()) ||
                line.getFurnaceLayer() != null && !Objects.equals(unit.getFurnaceLayer(), line.getFurnaceLayer()) ||
                unit.getLocationId() == null || unit.getStockLotId() == null) return false;
        try { return RdWeightSolver.grams(unit.getPackageWeight()) > 0; }
        catch (IllegalArgumentException ex) { return false; }
    }

    private static boolean validLotAndLocation(RdPackageUnitDO unit, RdStockLotDO lot, RdWarehouseLocationDO location, Long tenant) {
        return lot != null && location != null && Objects.equals(lot.getTenantId(), tenant) && Objects.equals(location.getTenantId(), tenant) &&
                Objects.equals(lot.getId(), unit.getStockLotId()) && Objects.equals(location.getId(), unit.getLocationId()) &&
                "AVAILABLE".equals(lot.getStatus()) && Boolean.TRUE.equals(location.getEnabled()) && Boolean.FALSE.equals(location.getFrozen()) &&
                location.getStocktakeOrderId() == null && "AVAILABLE".equals(location.getLocationStatus()) &&
                location.getLocationCode() != null && !location.getLocationCode().isBlank() &&
                Objects.equals(location.getLocationCode(), unit.getLocationCode()) && Objects.equals(lot.getLocationId(), unit.getLocationId()) &&
                Objects.equals(lot.getLocationCode(), unit.getLocationCode()) && Objects.equals(lot.getCustomerName(), unit.getCustomerName()) &&
                Objects.equals(lot.getProductModel(), unit.getProductModel()) && Objects.equals(lot.getFurnaceNo(), unit.getFurnaceNo()) &&
                Objects.equals(lot.getFurnaceLayer(), unit.getFurnaceLayer());
    }

    private static boolean quantity(BigDecimal value) { return value != null && value.signum() >= 0 && value.stripTrailingZeros().scale() <= 0; }
    private static int cap(BigDecimal value) { return value.signum() <= 0 ? 0 : value.min(BigDecimal.valueOf(500)).intValueExact(); }
}
