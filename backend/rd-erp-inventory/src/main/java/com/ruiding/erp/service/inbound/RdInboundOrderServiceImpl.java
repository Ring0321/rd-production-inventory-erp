package com.ruiding.erp.service.inbound;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.ruiding.erp.dal.dataobject.inbound.RdInboundOrderDO;
import com.ruiding.erp.dal.dataobject.inbound.RdInboundOrderLineDO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.masterdata.RdWarehouseLocationDO;
import com.ruiding.erp.dal.dataobject.production.RdProductionOutputDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockLotDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockTransactionDO;
import com.ruiding.erp.dal.mysql.inbound.RdInboundOrderLineMapper;
import com.ruiding.erp.dal.mysql.inbound.RdInboundOrderMapper;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.dal.mysql.masterdata.RdWarehouseLocationMapper;
import com.ruiding.erp.dal.mysql.production.RdProductionOutputMapper;
import com.ruiding.erp.dal.mysql.stock.RdStockLotMapper;
import com.ruiding.erp.dal.mysql.stock.RdStockTransactionMapper;
import com.ruiding.erp.enums.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.ruiding.erp.enums.ErrorCodeConstants.*;

@Service
public class RdInboundOrderServiceImpl implements RdInboundOrderService {
    @Resource private RdInboundOrderMapper orderMapper;
    @Resource private RdInboundOrderLineMapper lineMapper;
    @Resource private RdPackageUnitMapper packageMapper;
    @Resource private RdStockLotMapper stockLotMapper;
    @Resource private RdStockTransactionMapper transactionMapper;
    @Resource private RdProductionOutputMapper productionMapper;
    @Resource private RdWarehouseLocationMapper locationMapper;

    @Override @Transactional(rollbackFor = Exception.class)
    public Long create(LocalDate bizDate, Long productionOutputId, List<RdInboundOrderLineDO> lines, String remark) {
        if (lines == null || lines.isEmpty()) throw exception(INBOUND_PACKAGE_QC_REQUIRED);
        RdProductionOutputDO production = productionMapper.selectByIdForUpdate(productionOutputId);
        if (production == null) throw exception(PRODUCTION_OUTPUT_NOT_EXISTS);
        if (!RdProductionOutputStatusEnum.CONFIRMED.getStatus().equals(production.getStatus())) {
            throw exception(PRODUCTION_OUTPUT_STATUS_INVALID);
        }
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (RdInboundOrderLineDO line : lines) {
            RdPackageUnitDO unit = validatePackageForInbound(line.getPackageId());
            if (productionOutputId != null && !productionOutputId.equals(unit.getProductionOutputId())) {
                throw exception(INBOUND_PACKAGE_PRODUCTION_MISMATCH);
            }
            validateLocation(line);
            totalWeight = totalWeight.add(unit.getPackageWeight());
        }
        RdInboundOrderDO order = new RdInboundOrderDO()
                .setOrderNo("RK" + bizDate.toString().replace("-", "")
                        + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .setBizDate(bizDate).setSourceType("PRODUCTION_OUTPUT").setProductionOutputId(productionOutputId)
                .setTotalPackages(BigDecimal.valueOf(lines.size())).setTotalWeight(totalWeight)
                .setStatus(RdInboundOrderStatusEnum.DRAFT.getStatus()).setVersion(0).setRemark(remark);
        orderMapper.insert(order);
        for (RdInboundOrderLineDO line : lines) {
            RdPackageUnitDO unit = packageMapper.selectByIdForUpdate(line.getPackageId());
            unit.setStatus(RdPackageStatusEnum.INBOUND_PENDING.getStatus())
                    .setLocationId(line.getLocationId()).setLocationCode(line.getLocationCode());
            packageMapper.updateById(unit);
            line.setOrderId(order.getId()).setQuantity(BigDecimal.ONE).setWeight(unit.getPackageWeight());
            lineMapper.insert(line);
        }
        return order.getId();
    }

    private void validateLocation(RdInboundOrderLineDO line) {
        if (line.getLocationId() == null || line.getLocationCode() == null || line.getLocationCode().isBlank()) {
            throw exception(PRODUCTION_OUTPUT_INBOUND_FAIL_LOCATION_EMPTY);
        }
        RdWarehouseLocationDO location = locationMapper.selectById(line.getLocationId());
        if (location == null || !Boolean.TRUE.equals(location.getEnabled())
                || Boolean.TRUE.equals(location.getFrozen())
                || !line.getLocationCode().equals(location.getLocationCode())) {
            throw exception(INBOUND_LOCATION_INVALID);
        }
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void submit(Long id, Long operatorId, String operatorName) {
        RdInboundOrderDO order = requiredForUpdate(id);
        requireStatus(order, RdInboundOrderStatusEnum.DRAFT);
        order.setStatus(RdInboundOrderStatusEnum.SUBMITTED.getStatus())
                .setSubmitterId(operatorId).setSubmitterName(operatorName).setSubmitTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void verify(Long id, Long reviewerId, String reviewerName) {
        RdInboundOrderDO order = requiredForUpdate(id);
        requireStatus(order, RdInboundOrderStatusEnum.SUBMITTED);
        if (reviewerId.equals(order.getSubmitterId())) throw exception(INBOUND_ORDER_SELF_REVIEW);
        order.setStatus(RdInboundOrderStatusEnum.VERIFIED.getStatus())
                .setReviewerId(reviewerId).setReviewerName(reviewerName).setReviewTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void post(Long id, Long operatorId, String operatorName) {
        RdInboundOrderDO order = requiredForUpdate(id);
        if (RdInboundOrderStatusEnum.POSTED.getStatus().equals(order.getStatus())) return;
        requireStatus(order, RdInboundOrderStatusEnum.VERIFIED);
        for (RdInboundOrderLineDO line : lineMapper.selectListByOrderId(id)) postLine(order, line, operatorId, operatorName);
        order.setStatus(RdInboundOrderStatusEnum.POSTED.getStatus()).setPosterId(operatorId)
                .setPosterName(operatorName).setPostTime(LocalDateTime.now());
        orderMapper.updateById(order);
        updateProductionInboundStatus(order.getProductionOutputId());
    }

    private void postLine(RdInboundOrderDO order, RdInboundOrderLineDO line, Long operatorId, String operatorName) {
        String idempotencyKey = "INBOUND:" + order.getId() + ":" + line.getPackageId();
        if (transactionMapper.selectByIdempotencyKey(idempotencyKey) != null) return;
        RdPackageUnitDO unit = packageMapper.selectByIdForUpdate(line.getPackageId());
        if (unit == null || !RdPackageStatusEnum.INBOUND_PENDING.getStatus().equals(unit.getStatus())) {
            throw exception(INBOUND_PACKAGE_QC_REQUIRED);
        }
        Long tenantId = TenantContextHolder.getTenantId() == null ? 1L : TenantContextHolder.getTenantId();
        RdStockLotDO lot = stockLotMapper.selectInboundLotForUpdate(tenantId, line.getLocationCode(),
                unit.getCustomerName(), unit.getProductModel(), unit.getFurnaceNo(), unit.getFurnaceLayer());
        BigDecimal before;
        if (lot == null) {
            before = BigDecimal.ZERO;
            lot = new RdStockLotDO().setLocationId(line.getLocationId()).setLocationCode(line.getLocationCode())
                    .setCustomerName(unit.getCustomerName()).setProductModel(unit.getProductModel())
                    .setFurnaceNo(unit.getFurnaceNo()).setFurnaceLayer(unit.getFurnaceLayer())
                    .setOpeningQty(BigDecimal.ZERO).setTransferInQty(BigDecimal.ZERO).setTransferOutQty(BigDecimal.ZERO)
                    .setInboundQty(BigDecimal.ONE).setOutboundQty(BigDecimal.ZERO).setReturnQty(BigDecimal.ZERO)
                    .setWarehouseQty(BigDecimal.ONE).setLockedQty(BigDecimal.ZERO).setPackageWeight(unit.getPackageWeight())
                    .setStockWeight(unit.getPackageWeight()).setLastBizDate(order.getBizDate())
                    .setStatus(RdStockLotStatusEnum.AVAILABLE.getStatus()).setRemark("包码入库单生成");
            stockLotMapper.insert(lot);
        } else {
            before = zero(lot.getWarehouseQty());
            lot.setInboundQty(zero(lot.getInboundQty()).add(BigDecimal.ONE))
                    .setWarehouseQty(before.add(BigDecimal.ONE))
                    .setStockWeight(zero(lot.getStockWeight()).add(unit.getPackageWeight()))
                    .setLastBizDate(order.getBizDate()).setStatus(RdStockLotStatusEnum.AVAILABLE.getStatus());
            stockLotMapper.updateById(lot);
        }
        RdStockTransactionDO transaction = new RdStockTransactionDO().setBizDate(order.getBizDate())
                .setTransactionType(RdStockTransactionTypeEnum.PRODUCTION_IN.getType()).setStockLotId(lot.getId())
                .setPackageId(unit.getId()).setLocationId(line.getLocationId()).setLocationCode(line.getLocationCode())
                .setCustomerName(unit.getCustomerName()).setProductModel(unit.getProductModel())
                .setFurnaceNo(unit.getFurnaceNo()).setFurnaceLayer(unit.getFurnaceLayer())
                .setQuantity(BigDecimal.ONE).setPackageWeight(unit.getPackageWeight()).setWeight(unit.getPackageWeight())
                .setBeforeQty(before).setAfterQty(before.add(BigDecimal.ONE)).setSourceType("INBOUND_ORDER")
                .setSourceNo(order.getOrderNo()).setSourceLineNo(unit.getPackageCode()).setOperatorId(operatorId)
                .setOperatorName(operatorName).setReviewerId(order.getReviewerId()).setReviewerName(order.getReviewerName())
                .setIdempotencyKey(idempotencyKey).setRemark("质检合格包码入库");
        transactionMapper.insert(transaction);
        unit.setStatus(RdPackageStatusEnum.IN_STOCK.getStatus()).setStockLotId(lot.getId());
        packageMapper.updateById(unit);
        line.setStockLotId(lot.getId()).setStockTransactionId(transaction.getId());
        lineMapper.updateById(line);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void reverse(Long id, Long operatorId, String operatorName, String reason) {
        RdInboundOrderDO order = requiredForUpdate(id);
        requireStatus(order, RdInboundOrderStatusEnum.POSTED);
        for (RdInboundOrderLineDO line : lineMapper.selectListByOrderId(id)) {
            String key = "INBOUND-REVERSE:" + order.getId() + ":" + line.getPackageId();
            if (transactionMapper.selectByIdempotencyKey(key) != null) continue;
            RdPackageUnitDO unit = packageMapper.selectByIdForUpdate(line.getPackageId());
            if (unit == null || !RdPackageStatusEnum.IN_STOCK.getStatus().equals(unit.getStatus())) {
                throw exception(INBOUND_REVERSE_STOCK_USED);
            }
            RdStockLotDO lot = stockLotMapper.selectByIdForUpdate(line.getStockLotId());
            BigDecimal before = zero(lot.getWarehouseQty());
            BigDecimal after = before.subtract(BigDecimal.ONE);
            if (after.signum() < 0 || after.compareTo(zero(lot.getLockedQty())) < 0) {
                throw exception(INBOUND_REVERSE_STOCK_USED);
            }
            RdStockTransactionDO original = transactionMapper.selectById(line.getStockTransactionId());
            lot.setInboundQty(zero(lot.getInboundQty()).subtract(BigDecimal.ONE)).setWarehouseQty(after)
                    .setStockWeight(zero(lot.getStockWeight()).subtract(unit.getPackageWeight()))
                    .setStatus(after.signum() == 0 ? RdStockLotStatusEnum.EMPTY.getStatus() : RdStockLotStatusEnum.AVAILABLE.getStatus());
            stockLotMapper.updateById(lot);
            transactionMapper.insert(new RdStockTransactionDO().setBizDate(LocalDate.now())
                    .setTransactionType(RdStockTransactionTypeEnum.ADJUST_OUT.getType()).setStockLotId(lot.getId())
                    .setPackageId(unit.getId()).setLocationId(lot.getLocationId()).setLocationCode(lot.getLocationCode())
                    .setCustomerName(unit.getCustomerName()).setProductModel(unit.getProductModel())
                    .setFurnaceNo(unit.getFurnaceNo()).setFurnaceLayer(unit.getFurnaceLayer())
                    .setQuantity(BigDecimal.ONE.negate()).setPackageWeight(unit.getPackageWeight())
                    .setWeight(unit.getPackageWeight().negate()).setBeforeQty(before).setAfterQty(after)
                    .setSourceType("INBOUND_ORDER_REVERSE").setSourceNo(order.getOrderNo())
                    .setSourceLineNo(unit.getPackageCode()).setOperatorId(operatorId).setOperatorName(operatorName)
                    .setReviewerId(order.getReviewerId()).setReviewerName(order.getReviewerName())
                    .setIdempotencyKey(key).setReversalTransactionId(original == null ? null : original.getId())
                    .setRemark(reason));
            unit.setStatus(RdPackageStatusEnum.QC_PASSED.getStatus()).setStockLotId(null);
            packageMapper.updateById(unit);
        }
        order.setStatus(RdInboundOrderStatusEnum.REVERSED.getStatus()).setReverserId(operatorId)
                .setReverserName(operatorName).setReverseTime(LocalDateTime.now()).setReverseReason(reason);
        orderMapper.updateById(order);
        updateProductionInboundStatus(order.getProductionOutputId());
    }

    @Override public RdPackageUnitDO validatePackageForInbound(Long packageId) {
        RdPackageUnitDO unit = packageMapper.selectByIdForUpdate(packageId);
        if (unit == null) throw exception(PACKAGE_NOT_EXISTS);
        if (!RdPackageStatusEnum.QC_PASSED.getStatus().equals(unit.getStatus())) throw exception(INBOUND_PACKAGE_QC_REQUIRED);
        return unit;
    }

    private void updateProductionInboundStatus(Long productionOutputId) {
        if (productionOutputId == null || productionMapper == null) return;
        RdProductionOutputDO production = productionMapper.selectById(productionOutputId);
        if (production == null) return;
        List<RdPackageUnitDO> units = packageMapper.selectListByProductionOutputId(productionOutputId);
        long inbound = units.stream().filter(unit -> RdPackageStatusEnum.IN_STOCK.getStatus().equals(unit.getStatus())
                || RdPackageStatusEnum.OUTBOUND_PENDING.getStatus().equals(unit.getStatus())
                || RdPackageStatusEnum.OUTBOUNDED.getStatus().equals(unit.getStatus())).count();
        production.setInboundQty(BigDecimal.valueOf(inbound));
        production.setStatus(inbound == units.size() && !units.isEmpty()
                ? RdProductionOutputStatusEnum.INBOUNDED.getStatus() : RdProductionOutputStatusEnum.CONFIRMED.getStatus());
        productionMapper.updateById(production);
    }

    private RdInboundOrderDO requiredForUpdate(Long id) {
        RdInboundOrderDO order = orderMapper.selectByIdForUpdate(id);
        if (order == null) throw exception(INBOUND_ORDER_NOT_EXISTS);
        return order;
    }
    private static void requireStatus(RdInboundOrderDO order, RdInboundOrderStatusEnum status) {
        if (!status.getStatus().equals(order.getStatus())) throw exception(INBOUND_ORDER_STATUS_INVALID);
    }
    private static BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    @Override public RdInboundOrderDO get(Long id) {
        RdInboundOrderDO order = orderMapper.selectById(id);
        if (order == null) throw exception(INBOUND_ORDER_NOT_EXISTS);
        return order;
    }
    @Override public PageResult<RdInboundOrderDO> page(PageParam pageParam) {
        return orderMapper.selectPage(pageParam, new LambdaQueryWrapperX<RdInboundOrderDO>()
                .orderByDesc(RdInboundOrderDO::getBizDate).orderByDesc(RdInboundOrderDO::getId));
    }
}
