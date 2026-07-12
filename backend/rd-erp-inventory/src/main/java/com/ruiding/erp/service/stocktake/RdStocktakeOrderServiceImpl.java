package com.ruiding.erp.service.stocktake;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.ruiding.erp.controller.admin.stocktake.vo.RdStocktakePageReqVO;
import com.ruiding.erp.controller.admin.stocktake.vo.RdStocktakeScanReqVO;
import com.ruiding.erp.dal.dataobject.masterdata.RdWarehouseLocationDO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockLotDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockTransactionDO;
import com.ruiding.erp.dal.dataobject.stocktake.RdStocktakeOrderDO;
import com.ruiding.erp.dal.dataobject.stocktake.RdStocktakeOrderLineDO;
import com.ruiding.erp.dal.mysql.masterdata.RdWarehouseLocationMapper;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.dal.mysql.stock.RdStockLotMapper;
import com.ruiding.erp.dal.mysql.stock.RdStockTransactionMapper;
import com.ruiding.erp.dal.mysql.stocktake.RdStocktakeOrderLineMapper;
import com.ruiding.erp.dal.mysql.stocktake.RdStocktakeOrderMapper;
import com.ruiding.erp.enums.RdPackageStatusEnum;
import com.ruiding.erp.enums.RdStockLotStatusEnum;
import com.ruiding.erp.enums.RdStockTransactionTypeEnum;
import com.ruiding.erp.enums.RdStocktakeOrderStatusEnum;
import com.ruiding.erp.service.packageunit.RdPackageStatusMachine;
import com.ruiding.erp.service.packageunit.RdScanEventService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.ruiding.erp.enums.ErrorCodeConstants.STOCKTAKE_ADJUST_BELOW_LOCKED;
import static com.ruiding.erp.enums.ErrorCodeConstants.STOCKTAKE_LOCATION_FROZEN_BY_OTHER;
import static com.ruiding.erp.enums.ErrorCodeConstants.STOCKTAKE_LOCATION_INVALID;
import static com.ruiding.erp.enums.ErrorCodeConstants.STOCKTAKE_ORDER_NOT_EXISTS;
import static com.ruiding.erp.enums.ErrorCodeConstants.STOCKTAKE_ORDER_SELF_REVIEW;
import static com.ruiding.erp.enums.ErrorCodeConstants.STOCKTAKE_ORDER_STATUS_INVALID;
import static com.ruiding.erp.enums.ErrorCodeConstants.STOCKTAKE_ORDER_VERSION_CONFLICT;
import static com.ruiding.erp.enums.ErrorCodeConstants.STOCKTAKE_PACKAGE_DUPLICATE;
import static com.ruiding.erp.enums.ErrorCodeConstants.STOCKTAKE_PACKAGE_LOCATION_MISMATCH;
import static com.ruiding.erp.enums.ErrorCodeConstants.STOCKTAKE_PACKAGE_NOT_EXISTS;
import static com.ruiding.erp.enums.ErrorCodeConstants.STOCKTAKE_PACKAGE_STATUS_INVALID;

@Service
public class RdStocktakeOrderServiceImpl implements RdStocktakeOrderService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;

    @Resource private RdStocktakeOrderMapper orderMapper;
    @Resource private RdStocktakeOrderLineMapper lineMapper;
    @Resource private RdPackageUnitMapper packageMapper;
    @Resource private RdStockLotMapper stockLotMapper;
    @Resource private RdStockTransactionMapper transactionMapper;
    @Resource private RdWarehouseLocationMapper locationMapper;
    @Resource private RdScanEventService scanEventService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(Long locationId, LocalDate stocktakeDate, String remark) {
        RdWarehouseLocationDO location = requiredLocation(locationId);
        RdStocktakeOrderDO order = new RdStocktakeOrderDO()
                .setStocktakeNo("PD" + stocktakeDate.toString().replace("-", "")
                        + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .setStocktakeDate(stocktakeDate)
                .setLocationId(locationId)
                .setLocationCode(location.getLocationCode())
                .setStatus(RdStocktakeOrderStatusEnum.DRAFT.getStatus())
                .setVersion(0)
                .setBookPackageQty(ZERO)
                .setCountedPackageQty(ZERO)
                .setShortageQty(ZERO)
                .setSurplusQty(ZERO)
                .setBookWeight(ZERO)
                .setCountedWeight(ZERO)
                .setRemark(remark);
        orderMapper.insert(order);
        return order.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void freeze(Long id, Integer version) {
        RdStocktakeOrderDO order = requiredForUpdate(id);
        requireStatus(order, RdStocktakeOrderStatusEnum.DRAFT);
        requireVersion(order, version);
        RdWarehouseLocationDO location = requiredLocationForUpdate(order.getLocationId(), order.getLocationCode());
        if (Boolean.TRUE.equals(location.getFrozen()) && !Objects.equals(location.getStocktakeOrderId(), order.getId())) {
            throw exception(STOCKTAKE_LOCATION_FROZEN_BY_OTHER);
        }
        List<RdPackageUnitDO> units = packageMapper.selectListByLocationIdAndStatuses(
                order.getLocationId(), List.of(RdPackageStatusEnum.IN_STOCK.getStatus()));
        BigDecimal bookWeight = ZERO;
        for (RdPackageUnitDO unit : units) {
            lineMapper.insert(new RdStocktakeOrderLineDO()
                    .setOrderId(order.getId())
                    .setPackageId(unit.getId())
                    .setPackageCode(unit.getPackageCode())
                    .setStockLotId(unit.getStockLotId())
                    .setCustomerName(unit.getCustomerName())
                    .setProductModel(unit.getProductModel())
                    .setFurnaceNo(unit.getFurnaceNo())
                    .setFurnaceLayer(unit.getFurnaceLayer())
                    .setPackageWeight(unit.getPackageWeight())
                    .setBookQty(ONE)
                    .setCountedQty(ZERO)
                    .setDifferenceQty(ZERO)
                    .setResult("PENDING"));
            bookWeight = bookWeight.add(zero(unit.getPackageWeight()));
        }
        location.setFrozen(true);
        location.setStocktakeOrderId(order.getId());
        locationMapper.updateById(location);
        order.setStatus(RdStocktakeOrderStatusEnum.FROZEN.getStatus())
                .setSnapshotTime(LocalDateTime.now())
                .setBookPackageQty(BigDecimal.valueOf(units.size()))
                .setBookWeight(bookWeight)
                .setVersion(nextVersion(order));
        orderMapper.updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RdStocktakeOrderLineDO scan(RdStocktakeScanReqVO reqVO, Long operatorId, String operatorName) {
        RdStocktakeOrderDO order = requiredForUpdate(reqVO.getId());
        if (!RdStocktakeOrderStatusEnum.FROZEN.getStatus().equals(order.getStatus())
                && !RdStocktakeOrderStatusEnum.COUNTING.getStatus().equals(order.getStatus())) {
            throw exception(STOCKTAKE_ORDER_STATUS_INVALID);
        }
        RdPackageUnitDO unit = packageMapper.selectByPackageCodeForUpdate(reqVO.getPackageCode().trim());
        if (unit == null) {
            scanEventService.record("STOCKTAKE", reqVO.getId(), null, reqVO.getPackageCode(),
                    reqVO.getDeviceCode(), operatorId, operatorName, false, exception(STOCKTAKE_PACKAGE_NOT_EXISTS).getMessage());
            throw exception(STOCKTAKE_PACKAGE_NOT_EXISTS);
        }
        if (!Objects.equals(unit.getLocationId(), order.getLocationId())
                || !Objects.equals(unit.getLocationCode(), order.getLocationCode())) {
            scanEventService.record("STOCKTAKE", order.getId(), unit.getId(), unit.getPackageCode(),
                    reqVO.getDeviceCode(), operatorId, operatorName, false,
                    exception(STOCKTAKE_PACKAGE_LOCATION_MISMATCH).getMessage());
            throw exception(STOCKTAKE_PACKAGE_LOCATION_MISMATCH);
        }
        if (!RdPackageStatusEnum.IN_STOCK.getStatus().equals(unit.getStatus())
                && !RdPackageStatusEnum.STOCKTAKE_MISSING.getStatus().equals(unit.getStatus())) {
            scanEventService.record("STOCKTAKE", order.getId(), unit.getId(), unit.getPackageCode(),
                    reqVO.getDeviceCode(), operatorId, operatorName, false,
                    exception(STOCKTAKE_PACKAGE_STATUS_INVALID).getMessage());
            throw exception(STOCKTAKE_PACKAGE_STATUS_INVALID);
        }
        RdStocktakeOrderLineDO line = lineMapper.selectByOrderIdAndPackageId(order.getId(), unit.getId());
        if (line != null && zero(line.getCountedQty()).compareTo(ONE) >= 0) {
            scanEventService.record("STOCKTAKE", order.getId(), unit.getId(), unit.getPackageCode(),
                    reqVO.getDeviceCode(), operatorId, operatorName, false, null);
            throw exception(STOCKTAKE_PACKAGE_DUPLICATE);
        }
        if (line == null) {
            if (!RdPackageStatusEnum.STOCKTAKE_MISSING.getStatus().equals(unit.getStatus())) {
                scanEventService.record("STOCKTAKE", order.getId(), unit.getId(), unit.getPackageCode(),
                        reqVO.getDeviceCode(), operatorId, operatorName, false,
                        exception(STOCKTAKE_PACKAGE_NOT_EXISTS).getMessage());
                throw exception(STOCKTAKE_PACKAGE_NOT_EXISTS);
            }
            line = new RdStocktakeOrderLineDO()
                    .setOrderId(order.getId())
                    .setPackageId(unit.getId())
                    .setPackageCode(unit.getPackageCode())
                    .setStockLotId(unit.getStockLotId())
                    .setCustomerName(unit.getCustomerName())
                    .setProductModel(unit.getProductModel())
                    .setFurnaceNo(unit.getFurnaceNo())
                    .setFurnaceLayer(unit.getFurnaceLayer())
                    .setPackageWeight(unit.getPackageWeight())
                    .setBookQty(ZERO)
                    .setCountedQty(ZERO)
                    .setDifferenceQty(ZERO)
                    .setResult("PENDING");
            lineMapper.insert(line);
        }
        line.setCountedQty(ONE)
                .setDifferenceQty(ZERO)
                .setResult("COUNTED")
                .setScannerId(operatorId)
                .setScannerName(operatorName)
                .setScanTime(LocalDateTime.now());
        lineMapper.updateById(line);
        order.setStatus(RdStocktakeOrderStatusEnum.COUNTING.getStatus())
                .setVersion(nextVersion(order));
        orderMapper.updateById(order);
        scanEventService.record("STOCKTAKE", order.getId(), unit.getId(), unit.getPackageCode(),
                reqVO.getDeviceCode(), operatorId, operatorName, true, null);
        return line;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id, Integer version, Long operatorId, String operatorName) {
        RdStocktakeOrderDO order = requiredForUpdate(id);
        if (!RdStocktakeOrderStatusEnum.FROZEN.getStatus().equals(order.getStatus())
                && !RdStocktakeOrderStatusEnum.COUNTING.getStatus().equals(order.getStatus())) {
            throw exception(STOCKTAKE_ORDER_STATUS_INVALID);
        }
        requireVersion(order, version);
        Summary summary = summarize(lineMapper.selectListByOrderId(id));
        order.setStatus(RdStocktakeOrderStatusEnum.SUBMITTED.getStatus())
                .setBookPackageQty(summary.bookPackageQty)
                .setCountedPackageQty(summary.countedPackageQty)
                .setShortageQty(summary.shortageQty)
                .setSurplusQty(summary.surplusQty)
                .setBookWeight(summary.bookWeight)
                .setCountedWeight(summary.countedWeight)
                .setSubmitterId(operatorId)
                .setSubmitterName(operatorName)
                .setSubmitTime(LocalDateTime.now())
                .setVersion(nextVersion(order));
        orderMapper.updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, Integer version, Long reviewerId, String reviewerName) {
        RdStocktakeOrderDO order = requiredForUpdate(id);
        requireStatus(order, RdStocktakeOrderStatusEnum.SUBMITTED);
        requireVersion(order, version);
        if (Objects.equals(reviewerId, order.getSubmitterId())) {
            throw exception(STOCKTAKE_ORDER_SELF_REVIEW);
        }
        order.setStatus(RdStocktakeOrderStatusEnum.APPROVED.getStatus())
                .setReviewerId(reviewerId)
                .setReviewerName(reviewerName)
                .setReviewTime(LocalDateTime.now())
                .setVersion(nextVersion(order));
        orderMapper.updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjust(Long id, Integer version, Long operatorId, String operatorName) {
        RdStocktakeOrderDO order = requiredForUpdate(id);
        if (RdStocktakeOrderStatusEnum.ADJUSTED.getStatus().equals(order.getStatus())) {
            return;
        }
        requireStatus(order, RdStocktakeOrderStatusEnum.APPROVED);
        requireVersion(order, version);
        for (RdStocktakeOrderLineDO line : lineMapper.selectListByOrderId(id)) {
            BigDecimal differenceQty = resolveDifference(line);
            if (differenceQty.signum() < 0) {
                adjustShortage(order, line, operatorId, operatorName);
            } else if (differenceQty.signum() > 0) {
                adjustSurplus(order, line, operatorId, operatorName);
            }
        }
        order.setStatus(RdStocktakeOrderStatusEnum.ADJUSTED.getStatus())
                .setAdjusterId(operatorId)
                .setAdjusterName(operatorName)
                .setAdjustTime(LocalDateTime.now())
                .setVersion(nextVersion(order));
        orderMapper.updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id, Integer version, Long operatorId, String operatorName) {
        RdStocktakeOrderDO order = requiredForUpdate(id);
        requireStatus(order, RdStocktakeOrderStatusEnum.ADJUSTED);
        requireVersion(order, version);
        releaseLocationIfMatched(order);
        order.setStatus(RdStocktakeOrderStatusEnum.CLOSED.getStatus())
                .setCloserId(operatorId)
                .setCloserName(operatorName)
                .setCloseTime(LocalDateTime.now())
                .setVersion(nextVersion(order));
        orderMapper.updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, Integer version, Long operatorId, String operatorName) {
        RdStocktakeOrderDO order = requiredForUpdate(id);
        if (RdStocktakeOrderStatusEnum.ADJUSTED.getStatus().equals(order.getStatus())
                || RdStocktakeOrderStatusEnum.CLOSED.getStatus().equals(order.getStatus())) {
            throw exception(STOCKTAKE_ORDER_STATUS_INVALID);
        }
        requireVersion(order, version);
        releaseLocationIfMatched(order);
        order.setStatus(RdStocktakeOrderStatusEnum.CANCELLED.getStatus())
                .setCloserId(operatorId)
                .setCloserName(operatorName)
                .setCloseTime(LocalDateTime.now())
                .setVersion(nextVersion(order));
        orderMapper.updateById(order);
    }

    @Override
    public RdStocktakeOrderDO get(Long id) {
        RdStocktakeOrderDO order = orderMapper.selectById(id);
        if (order == null) {
            throw exception(STOCKTAKE_ORDER_NOT_EXISTS);
        }
        return order;
    }

    @Override
    public List<RdStocktakeOrderLineDO> getLines(Long id) {
        get(id);
        List<RdStocktakeOrderLineDO> lines = lineMapper.selectListByOrderId(id);
        for (RdStocktakeOrderLineDO line : lines) {
            refreshDifference(line);
        }
        return lines;
    }

    @Override
    public List<RdStocktakeOrderLineDO> getDifference(Long id) {
        List<RdStocktakeOrderLineDO> result = new ArrayList<>();
        for (RdStocktakeOrderLineDO line : getLines(id)) {
            if (resolveDifference(line).signum() != 0) {
                result.add(line);
            }
        }
        return result;
    }

    @Override
    public PageResult<RdStocktakeOrderDO> page(RdStocktakePageReqVO reqVO) {
        return orderMapper.selectPage(reqVO);
    }

    private void adjustShortage(RdStocktakeOrderDO order, RdStocktakeOrderLineDO line,
                                Long operatorId, String operatorName) {
        String key = "STOCKTAKE:" + order.getId() + ":" + line.getPackageId() + ":OUT";
        if (transactionMapper.selectByIdempotencyKey(key) != null) {
            return;
        }
        RdPackageUnitDO unit = packageMapper.selectByIdForUpdate(line.getPackageId());
        RdStockLotDO lot = stockLotMapper.selectByIdForUpdate(line.getStockLotId());
        BigDecimal before = zero(lot.getWarehouseQty());
        BigDecimal locked = zero(lot.getLockedQty());
        BigDecimal after = before.subtract(ONE);
        if (after.compareTo(locked) < 0) {
            throw exception(STOCKTAKE_ADJUST_BELOW_LOCKED);
        }
        lot.setWarehouseQty(after)
                .setStockWeight(zero(lot.getStockWeight()).subtract(zero(unit.getPackageWeight())))
                .setStatus(resolveStockStatus(after, locked));
        stockLotMapper.updateById(lot);
        transactionMapper.insert(buildTransaction(order, line, unit, RdStockTransactionTypeEnum.ADJUST_OUT.getType(),
                ONE.negate(), before, after, key, operatorId, operatorName));
        RdPackageStatusMachine.requireTransition(RdPackageStatusEnum.valueOf(unit.getStatus()),
                RdPackageStatusEnum.STOCKTAKE_MISSING);
        unit.setStatus(RdPackageStatusEnum.STOCKTAKE_MISSING.getStatus());
        packageMapper.updateById(unit);
    }

    private void adjustSurplus(RdStocktakeOrderDO order, RdStocktakeOrderLineDO line,
                               Long operatorId, String operatorName) {
        String key = "STOCKTAKE:" + order.getId() + ":" + line.getPackageId() + ":IN";
        if (transactionMapper.selectByIdempotencyKey(key) != null) {
            return;
        }
        RdPackageUnitDO unit = packageMapper.selectByIdForUpdate(line.getPackageId());
        RdStockLotDO lot = stockLotMapper.selectByIdForUpdate(line.getStockLotId());
        BigDecimal before = zero(lot.getWarehouseQty());
        BigDecimal locked = zero(lot.getLockedQty());
        BigDecimal after = before.add(ONE);
        lot.setWarehouseQty(after)
                .setStockWeight(zero(lot.getStockWeight()).add(zero(unit.getPackageWeight())))
                .setStatus(resolveStockStatus(after, locked));
        stockLotMapper.updateById(lot);
        transactionMapper.insert(buildTransaction(order, line, unit, RdStockTransactionTypeEnum.ADJUST_IN.getType(),
                ONE, before, after, key, operatorId, operatorName));
        RdPackageStatusMachine.requireTransition(RdPackageStatusEnum.valueOf(unit.getStatus()),
                RdPackageStatusEnum.IN_STOCK);
        unit.setStatus(RdPackageStatusEnum.IN_STOCK.getStatus());
        packageMapper.updateById(unit);
    }

    private RdStockTransactionDO buildTransaction(RdStocktakeOrderDO order, RdStocktakeOrderLineDO line,
                                                  RdPackageUnitDO unit, String transactionType,
                                                  BigDecimal quantity, BigDecimal beforeQty, BigDecimal afterQty,
                                                  String idempotencyKey, Long operatorId, String operatorName) {
        return new RdStockTransactionDO()
                .setBizDate(order.getStocktakeDate())
                .setTransactionType(transactionType)
                .setStockLotId(line.getStockLotId())
                .setPackageId(line.getPackageId())
                .setLocationId(order.getLocationId())
                .setLocationCode(order.getLocationCode())
                .setCustomerName(line.getCustomerName())
                .setProductModel(line.getProductModel())
                .setFurnaceNo(line.getFurnaceNo())
                .setFurnaceLayer(line.getFurnaceLayer())
                .setQuantity(quantity)
                .setPackageWeight(unit.getPackageWeight())
                .setWeight(quantity.multiply(zero(unit.getPackageWeight())))
                .setBeforeQty(beforeQty)
                .setAfterQty(afterQty)
                .setSourceType("STOCKTAKE")
                .setSourceNo(order.getStocktakeNo())
                .setSourceLineNo(line.getPackageCode())
                .setOperatorId(operatorId)
                .setOperatorName(operatorName)
                .setReviewerId(order.getReviewerId())
                .setReviewerName(order.getReviewerName())
                .setIdempotencyKey(idempotencyKey)
                .setRemark("Stocktake adjustment");
    }

    private void releaseLocationIfMatched(RdStocktakeOrderDO order) {
        RdWarehouseLocationDO location = requiredLocationForUpdate(order.getLocationId(), order.getLocationCode());
        if (Boolean.TRUE.equals(location.getFrozen()) && Objects.equals(location.getStocktakeOrderId(), order.getId())) {
            location.setFrozen(false);
            location.setStocktakeOrderId(null);
            locationMapper.updateById(location);
        }
    }

    private RdStocktakeOrderDO requiredForUpdate(Long id) {
        RdStocktakeOrderDO order = orderMapper.selectByIdForUpdate(id);
        if (order == null) {
            throw exception(STOCKTAKE_ORDER_NOT_EXISTS);
        }
        return order;
    }

    private RdWarehouseLocationDO requiredLocation(Long locationId) {
        RdWarehouseLocationDO location = locationMapper.selectById(locationId);
        if (location == null || !Boolean.TRUE.equals(location.getEnabled())) {
            throw exception(STOCKTAKE_LOCATION_INVALID);
        }
        return location;
    }

    private RdWarehouseLocationDO requiredLocationForUpdate(Long locationId, String locationCode) {
        RdWarehouseLocationDO location = locationMapper.selectByIdForUpdate(locationId);
        if (location == null || !Boolean.TRUE.equals(location.getEnabled())
                || !Objects.equals(location.getLocationCode(), locationCode)) {
            throw exception(STOCKTAKE_LOCATION_INVALID);
        }
        return location;
    }

    private void requireStatus(RdStocktakeOrderDO order, RdStocktakeOrderStatusEnum status) {
        if (!status.getStatus().equals(order.getStatus())) {
            throw exception(STOCKTAKE_ORDER_STATUS_INVALID);
        }
    }

    private void requireVersion(RdStocktakeOrderDO order, Integer version) {
        if (version != null && !Objects.equals(order.getVersion(), version)) {
            throw exception(STOCKTAKE_ORDER_VERSION_CONFLICT);
        }
    }

    private int nextVersion(RdStocktakeOrderDO order) {
        return order.getVersion() == null ? 1 : order.getVersion() + 1;
    }

    private Summary summarize(List<RdStocktakeOrderLineDO> lines) {
        Summary summary = new Summary();
        for (RdStocktakeOrderLineDO line : lines) {
            refreshDifference(line);
            if (zero(line.getBookQty()).signum() > 0) {
                summary.bookPackageQty = summary.bookPackageQty.add(line.getBookQty());
                summary.bookWeight = summary.bookWeight.add(zero(line.getPackageWeight()).multiply(line.getBookQty()));
            }
            if (zero(line.getCountedQty()).signum() > 0) {
                summary.countedPackageQty = summary.countedPackageQty.add(line.getCountedQty());
                summary.countedWeight = summary.countedWeight.add(zero(line.getPackageWeight()).multiply(line.getCountedQty()));
            }
            if (zero(line.getDifferenceQty()).signum() < 0) {
                summary.shortageQty = summary.shortageQty.add(line.getDifferenceQty().abs());
            } else if (zero(line.getDifferenceQty()).signum() > 0) {
                summary.surplusQty = summary.surplusQty.add(line.getDifferenceQty());
            }
            lineMapper.updateById(line);
        }
        return summary;
    }

    private void refreshDifference(RdStocktakeOrderLineDO line) {
        BigDecimal differenceQty = resolveDifference(line);
        line.setDifferenceQty(differenceQty);
        line.setResult(differenceQty.signum() < 0 ? "SHORTAGE" : differenceQty.signum() > 0 ? "SURPLUS" : "MATCH");
    }

    private BigDecimal resolveDifference(RdStocktakeOrderLineDO line) {
        return zero(line.getCountedQty()).subtract(zero(line.getBookQty()));
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private static String resolveStockStatus(BigDecimal warehouseQty, BigDecimal lockedQty) {
        if (warehouseQty.signum() == 0) {
            return RdStockLotStatusEnum.EMPTY.getStatus();
        }
        if (lockedQty.signum() > 0 && lockedQty.compareTo(warehouseQty) >= 0) {
            return RdStockLotStatusEnum.LOCKED.getStatus();
        }
        return RdStockLotStatusEnum.AVAILABLE.getStatus();
    }

    private static final class Summary {
        private BigDecimal bookPackageQty = ZERO;
        private BigDecimal countedPackageQty = ZERO;
        private BigDecimal shortageQty = ZERO;
        private BigDecimal surplusQty = ZERO;
        private BigDecimal bookWeight = ZERO;
        private BigDecimal countedWeight = ZERO;
    }
}
