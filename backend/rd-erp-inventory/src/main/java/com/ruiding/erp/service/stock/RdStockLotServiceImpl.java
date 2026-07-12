package com.ruiding.erp.service.stock;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.ruiding.erp.controller.admin.stock.vo.RdStockAdjustmentCreateReqVO;
import com.ruiding.erp.controller.admin.stock.vo.RdStockLotPageReqVO;
import com.ruiding.erp.controller.admin.stock.vo.RdStockOpeningCreateReqVO;
import com.ruiding.erp.dal.dataobject.production.RdProductionOutputDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockLotDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockTransactionDO;
import com.ruiding.erp.dal.mysql.stock.RdStockLotMapper;
import com.ruiding.erp.enums.RdLogRecordConstants;
import com.ruiding.erp.enums.RdStockLotStatusEnum;
import com.ruiding.erp.enums.RdStockTransactionTypeEnum;
import com.ruiding.erp.service.masterdata.RdCustomerProductPair;
import com.ruiding.erp.service.masterdata.RdCustomerProductService;
import com.ruiding.erp.service.masterdata.RdWarehouseLocationFreezeGuard;
import com.mzt.logapi.context.LogRecordContext;
import com.mzt.logapi.starter.annotation.LogRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertMap;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserNickname;
import static com.ruiding.erp.enums.ErrorCodeConstants.STOCK_LOT_ADJUST_CLOSED;
import static com.ruiding.erp.enums.ErrorCodeConstants.STOCK_LOT_ADJUST_BELOW_LOCKED;
import static com.ruiding.erp.enums.ErrorCodeConstants.STOCK_LOT_ADJUST_NEGATIVE_BALANCE;
import static com.ruiding.erp.enums.ErrorCodeConstants.STOCK_LOT_ADJUST_TYPE_INVALID;
import static com.ruiding.erp.enums.ErrorCodeConstants.STOCK_LOT_NOT_EXISTS;

/**
 * RD stock lot Service implementation.
 */
@Service
@Validated
public class RdStockLotServiceImpl implements RdStockLotService {

    private static final String SOURCE_TYPE_PRODUCTION_OUTPUT = "PRODUCTION_OUTPUT";
    private static final String SOURCE_TYPE_STOCK_OPENING = "STOCK_OPENING";
    private static final String SOURCE_TYPE_STOCK_ADJUSTMENT = "STOCK_ADJUSTMENT";
    private static final String SOURCE_TYPE_EXCEL_IMPORT = "EXCEL_IMPORT";

    @Resource
    private RdStockLotMapper stockLotMapper;
    @Resource
    private RdStockTransactionService stockTransactionService;
    @Resource
    private RdCustomerProductService customerProductService;
    @Resource
    private RdWarehouseLocationFreezeGuard locationFreezeGuard;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RdStockLotDO createProductionInbound(RdProductionOutputDO productionOutput) {
        locationFreezeGuard.requireMovable(productionOutput.getLocationId());
        RdCustomerProductPair pair = customerProductService.recordPair(
                productionOutput.getCustomerName(), productionOutput.getProductModel());
        BigDecimal quantity = productionOutput.getOutputQty();
        BigDecimal stockWeight = quantity.multiply(productionOutput.getPackageWeight());
        RdStockLotDO stockLot = new RdStockLotDO()
                .setLocationId(productionOutput.getLocationId())
                .setLocationCode(productionOutput.getLocationCode())
                .setCustomerName(pair.getCustomerName())
                .setProductModel(pair.getProductModel())
                .setFurnaceNo(productionOutput.getFurnaceNo())
                .setFurnaceLayer(productionOutput.getFurnaceLayer())
                .setOpeningQty(BigDecimal.ZERO)
                .setTransferInQty(BigDecimal.ZERO)
                .setTransferOutQty(BigDecimal.ZERO)
                .setInboundQty(quantity)
                .setOutboundQty(BigDecimal.ZERO)
                .setReturnQty(BigDecimal.ZERO)
                .setWarehouseQty(quantity)
                .setLockedQty(BigDecimal.ZERO)
                .setPackageWeight(productionOutput.getPackageWeight())
                .setStockWeight(stockWeight)
                .setLastBizDate(productionOutput.getProductionDate())
                .setStatus(RdStockLotStatusEnum.AVAILABLE.getStatus())
                .setRemark("由生产炉层产量入库生成");
        stockLotMapper.insert(stockLot);

        stockTransactionService.createStockTransaction(new RdStockTransactionDO()
                .setBizDate(productionOutput.getProductionDate())
                .setTransactionType(RdStockTransactionTypeEnum.PRODUCTION_IN.getType())
                .setStockLotId(stockLot.getId())
                .setLocationId(stockLot.getLocationId())
                .setLocationCode(stockLot.getLocationCode())
                .setCustomerName(stockLot.getCustomerName())
                .setProductModel(stockLot.getProductModel())
                .setFurnaceNo(stockLot.getFurnaceNo())
                .setFurnaceLayer(stockLot.getFurnaceLayer())
                .setQuantity(quantity)
                .setPackageWeight(stockLot.getPackageWeight())
                .setWeight(stockWeight)
                .setBeforeQty(BigDecimal.ZERO)
                .setAfterQty(quantity)
                .setSourceType(SOURCE_TYPE_PRODUCTION_OUTPUT)
                .setSourceNo(String.valueOf(productionOutput.getId()))
                .setSourceLineNo(productionOutput.getFurnaceLayer())
                .setOperatorId(getLoginUserId())
                .setOperatorName(getLoginUserNickname())
                .setRemark("生产产出转入库"));
        return stockLot;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = RdLogRecordConstants.STOCK_TYPE,
            subType = RdLogRecordConstants.STOCK_OPENING_SUB_TYPE,
            bizNo = RdLogRecordConstants.BIZ_ID_EXPRESSION,
            success = RdLogRecordConstants.STOCK_OPENING_SUCCESS)
    public Long createOpeningStock(RdStockOpeningCreateReqVO createReqVO,
                                   Long operatorId, String operatorName) {
        Long stockLotId = createOpeningStock(createReqVO, SOURCE_TYPE_STOCK_OPENING, null, null,
                operatorId, operatorName);
        LogRecordContext.putVariable(RdLogRecordConstants.BIZ_ID_VARIABLE, stockLotId);
        return stockLotId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createImportedOpeningStock(RdStockOpeningCreateReqVO createReqVO,
                                           Long importTaskId, Integer excelRowNo,
                                           Long operatorId, String operatorName) {
        return createOpeningStock(createReqVO, SOURCE_TYPE_EXCEL_IMPORT,
                String.valueOf(importTaskId), String.valueOf(excelRowNo), operatorId, operatorName);
    }

    private Long createOpeningStock(RdStockOpeningCreateReqVO createReqVO,
                                    String sourceType, String sourceNo, String sourceLineNo,
                                    Long operatorId, String operatorName) {
        locationFreezeGuard.requireMovable(createReqVO.getLocationId());
        RdCustomerProductPair pair = customerProductService.recordPair(
                createReqVO.getCustomerName(), createReqVO.getProductModel());
        BigDecimal stockWeight = createReqVO.getQuantity().multiply(createReqVO.getPackageWeight());
        RdStockLotDO stockLot = new RdStockLotDO()
                .setLocationId(createReqVO.getLocationId())
                .setLocationCode(createReqVO.getLocationCode())
                .setCustomerName(pair.getCustomerName())
                .setProductModel(pair.getProductModel())
                .setFurnaceNo(createReqVO.getFurnaceNo())
                .setFurnaceLayer(createReqVO.getFurnaceLayer())
                .setOpeningQty(createReqVO.getQuantity())
                .setTransferInQty(BigDecimal.ZERO)
                .setTransferOutQty(BigDecimal.ZERO)
                .setInboundQty(BigDecimal.ZERO)
                .setOutboundQty(BigDecimal.ZERO)
                .setReturnQty(BigDecimal.ZERO)
                .setWarehouseQty(createReqVO.getQuantity())
                .setLockedQty(BigDecimal.ZERO)
                .setPackageWeight(createReqVO.getPackageWeight())
                .setStockWeight(stockWeight)
                .setLastBizDate(createReqVO.getBizDate())
                .setStatus(RdStockLotStatusEnum.AVAILABLE.getStatus())
                .setRemark(createReqVO.getRemark());
        stockLotMapper.insert(stockLot);

        stockTransactionService.createStockTransaction(new RdStockTransactionDO()
                .setBizDate(createReqVO.getBizDate())
                .setTransactionType(RdStockTransactionTypeEnum.OPENING.getType())
                .setStockLotId(stockLot.getId())
                .setLocationId(stockLot.getLocationId())
                .setLocationCode(stockLot.getLocationCode())
                .setCustomerName(stockLot.getCustomerName())
                .setProductModel(stockLot.getProductModel())
                .setFurnaceNo(stockLot.getFurnaceNo())
                .setFurnaceLayer(stockLot.getFurnaceLayer())
                .setQuantity(createReqVO.getQuantity())
                .setPackageWeight(stockLot.getPackageWeight())
                .setWeight(stockWeight)
                .setBeforeQty(BigDecimal.ZERO)
                .setAfterQty(createReqVO.getQuantity())
                .setSourceType(sourceType)
                .setSourceNo(sourceNo == null ? String.valueOf(stockLot.getId()) : sourceNo)
                .setSourceLineNo(sourceLineNo == null ? stockLot.getFurnaceLayer() : sourceLineNo)
                .setOperatorId(operatorId)
                .setOperatorName(operatorName)
                .setRemark(createReqVO.getRemark()));
        return stockLot.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = RdLogRecordConstants.STOCK_TYPE,
            subType = RdLogRecordConstants.STOCK_ADJUST_SUB_TYPE,
            bizNo = "{{#createReqVO.stockLotId}}", success = RdLogRecordConstants.STOCK_ADJUST_SUCCESS)
    public Long adjustStock(RdStockAdjustmentCreateReqVO createReqVO,
                            Long operatorId, String operatorName) {
        String adjustmentType = createReqVO.getAdjustmentType();
        boolean adjustIn = RdStockTransactionTypeEnum.ADJUST_IN.getType().equals(adjustmentType);
        if (!adjustIn && !RdStockTransactionTypeEnum.ADJUST_OUT.getType().equals(adjustmentType)) {
            throw exception(STOCK_LOT_ADJUST_TYPE_INVALID);
        }

        RdStockLotDO stockLot = stockLotMapper.selectByIdForUpdate(createReqVO.getStockLotId());
        if (stockLot == null) {
            throw exception(STOCK_LOT_NOT_EXISTS);
        }
        locationFreezeGuard.requireMovable(stockLot.getLocationId());
        if (RdStockLotStatusEnum.CLOSED.getStatus().equals(stockLot.getStatus())) {
            throw exception(STOCK_LOT_ADJUST_CLOSED);
        }

        BigDecimal beforeQty = stockLot.getWarehouseQty();
        BigDecimal movementQty = adjustIn ? createReqVO.getQuantity() : createReqVO.getQuantity().negate();
        BigDecimal afterQty = beforeQty.add(movementQty);
        if (afterQty.signum() < 0) {
            throw exception(STOCK_LOT_ADJUST_NEGATIVE_BALANCE);
        }
        if (afterQty.compareTo(zeroIfNull(stockLot.getLockedQty())) < 0) {
            throw exception(STOCK_LOT_ADJUST_BELOW_LOCKED);
        }

        stockLot.setWarehouseQty(afterQty)
                .setStockWeight(afterQty.multiply(stockLot.getPackageWeight()))
                .setLastBizDate(maxBizDate(stockLot.getLastBizDate(), createReqVO.getBizDate()));
        if (afterQty.signum() == 0) {
            stockLot.setStatus(RdStockLotStatusEnum.EMPTY.getStatus());
        } else if (RdStockLotStatusEnum.EMPTY.getStatus().equals(stockLot.getStatus())) {
            stockLot.setStatus(RdStockLotStatusEnum.AVAILABLE.getStatus());
        }
        stockLotMapper.updateById(stockLot);

        return stockTransactionService.createStockTransaction(new RdStockTransactionDO()
                .setBizDate(createReqVO.getBizDate())
                .setTransactionType(adjustmentType)
                .setStockLotId(stockLot.getId())
                .setLocationId(stockLot.getLocationId())
                .setLocationCode(stockLot.getLocationCode())
                .setCustomerName(stockLot.getCustomerName())
                .setProductModel(stockLot.getProductModel())
                .setFurnaceNo(stockLot.getFurnaceNo())
                .setFurnaceLayer(stockLot.getFurnaceLayer())
                .setQuantity(movementQty)
                .setPackageWeight(stockLot.getPackageWeight())
                .setWeight(movementQty.multiply(stockLot.getPackageWeight()))
                .setBeforeQty(beforeQty)
                .setAfterQty(afterQty)
                .setSourceType(SOURCE_TYPE_STOCK_ADJUSTMENT)
                .setSourceNo(String.valueOf(stockLot.getId()))
                .setSourceLineNo(stockLot.getFurnaceLayer())
                .setOperatorId(operatorId)
                .setOperatorName(operatorName)
                .setRemark(createReqVO.getRemark()));
    }

    private static LocalDate maxBizDate(LocalDate currentBizDate, LocalDate newBizDate) {
        return currentBizDate == null || newBizDate.isAfter(currentBizDate) ? newBizDate : currentBizDate;
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    @Override
    public RdStockLotDO getStockLot(Long id) {
        RdStockLotDO stockLot = stockLotMapper.selectById(id);
        if (stockLot == null) {
            throw exception(STOCK_LOT_NOT_EXISTS);
        }
        return stockLot;
    }

    @Override
    public Map<Long, RdStockLotDO> getStockLotMap(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return convertMap(stockLotMapper.selectByIds(ids), RdStockLotDO::getId);
    }

    @Override
    public PageResult<RdStockLotDO> getStockLotPage(RdStockLotPageReqVO pageReqVO) {
        return stockLotMapper.selectPage(pageReqVO);
    }

}
