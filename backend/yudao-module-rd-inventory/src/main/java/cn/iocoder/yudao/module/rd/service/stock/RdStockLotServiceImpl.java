package cn.iocoder.yudao.module.rd.service.stock;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rd.controller.admin.stock.vo.RdStockAdjustmentCreateReqVO;
import cn.iocoder.yudao.module.rd.controller.admin.stock.vo.RdStockLotPageReqVO;
import cn.iocoder.yudao.module.rd.controller.admin.stock.vo.RdStockOpeningCreateReqVO;
import cn.iocoder.yudao.module.rd.dal.dataobject.production.RdProductionOutputDO;
import cn.iocoder.yudao.module.rd.dal.dataobject.stock.RdStockLotDO;
import cn.iocoder.yudao.module.rd.dal.dataobject.stock.RdStockTransactionDO;
import cn.iocoder.yudao.module.rd.dal.mysql.stock.RdStockLotMapper;
import cn.iocoder.yudao.module.rd.enums.RdStockLotStatusEnum;
import cn.iocoder.yudao.module.rd.enums.RdStockTransactionTypeEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserNickname;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.STOCK_LOT_ADJUST_CLOSED;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.STOCK_LOT_ADJUST_NEGATIVE_BALANCE;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.STOCK_LOT_ADJUST_TYPE_INVALID;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.STOCK_LOT_NOT_EXISTS;

/**
 * RD stock lot Service implementation.
 */
@Service
@Validated
public class RdStockLotServiceImpl implements RdStockLotService {

    private static final String SOURCE_TYPE_PRODUCTION_OUTPUT = "PRODUCTION_OUTPUT";
    private static final String SOURCE_TYPE_STOCK_OPENING = "STOCK_OPENING";
    private static final String SOURCE_TYPE_STOCK_ADJUSTMENT = "STOCK_ADJUSTMENT";

    @Resource
    private RdStockLotMapper stockLotMapper;
    @Resource
    private RdStockTransactionService stockTransactionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RdStockLotDO createProductionInbound(RdProductionOutputDO productionOutput) {
        BigDecimal quantity = productionOutput.getOutputQty();
        BigDecimal stockWeight = quantity.multiply(productionOutput.getPackageWeight());
        RdStockLotDO stockLot = new RdStockLotDO()
                .setLocationId(productionOutput.getLocationId())
                .setLocationCode(productionOutput.getLocationCode())
                .setCustomerName(productionOutput.getCustomerName())
                .setProductModel(productionOutput.getProductModel())
                .setFurnaceNo(productionOutput.getFurnaceNo())
                .setFurnaceLayer(productionOutput.getFurnaceLayer())
                .setOpeningQty(BigDecimal.ZERO)
                .setTransferInQty(BigDecimal.ZERO)
                .setTransferOutQty(BigDecimal.ZERO)
                .setInboundQty(quantity)
                .setOutboundQty(BigDecimal.ZERO)
                .setReturnQty(BigDecimal.ZERO)
                .setWarehouseQty(quantity)
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
    public Long createOpeningStock(RdStockOpeningCreateReqVO createReqVO,
                                   Long operatorId, String operatorName) {
        BigDecimal stockWeight = createReqVO.getQuantity().multiply(createReqVO.getPackageWeight());
        RdStockLotDO stockLot = new RdStockLotDO()
                .setLocationId(createReqVO.getLocationId())
                .setLocationCode(createReqVO.getLocationCode())
                .setCustomerName(createReqVO.getCustomerName())
                .setProductModel(createReqVO.getProductModel())
                .setFurnaceNo(createReqVO.getFurnaceNo())
                .setFurnaceLayer(createReqVO.getFurnaceLayer())
                .setOpeningQty(createReqVO.getQuantity())
                .setTransferInQty(BigDecimal.ZERO)
                .setTransferOutQty(BigDecimal.ZERO)
                .setInboundQty(BigDecimal.ZERO)
                .setOutboundQty(BigDecimal.ZERO)
                .setReturnQty(BigDecimal.ZERO)
                .setWarehouseQty(createReqVO.getQuantity())
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
                .setSourceType(SOURCE_TYPE_STOCK_OPENING)
                .setSourceNo(String.valueOf(stockLot.getId()))
                .setSourceLineNo(stockLot.getFurnaceLayer())
                .setOperatorId(operatorId)
                .setOperatorName(operatorName)
                .setRemark(createReqVO.getRemark()));
        return stockLot.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
        if (RdStockLotStatusEnum.CLOSED.getStatus().equals(stockLot.getStatus())) {
            throw exception(STOCK_LOT_ADJUST_CLOSED);
        }

        BigDecimal beforeQty = stockLot.getWarehouseQty();
        BigDecimal movementQty = adjustIn ? createReqVO.getQuantity() : createReqVO.getQuantity().negate();
        BigDecimal afterQty = beforeQty.add(movementQty);
        if (afterQty.signum() < 0) {
            throw exception(STOCK_LOT_ADJUST_NEGATIVE_BALANCE);
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

    @Override
    public RdStockLotDO getStockLot(Long id) {
        RdStockLotDO stockLot = stockLotMapper.selectById(id);
        if (stockLot == null) {
            throw exception(STOCK_LOT_NOT_EXISTS);
        }
        return stockLot;
    }

    @Override
    public PageResult<RdStockLotDO> getStockLotPage(RdStockLotPageReqVO pageReqVO) {
        return stockLotMapper.selectPage(pageReqVO);
    }

}
