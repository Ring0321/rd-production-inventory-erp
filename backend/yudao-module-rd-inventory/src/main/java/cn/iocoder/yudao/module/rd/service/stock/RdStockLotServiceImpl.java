package cn.iocoder.yudao.module.rd.service.stock;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rd.controller.admin.stock.vo.RdStockLotPageReqVO;
import cn.iocoder.yudao.module.rd.dal.dataobject.production.RdProductionOutputDO;
import cn.iocoder.yudao.module.rd.dal.dataobject.stock.RdStockLotDO;
import cn.iocoder.yudao.module.rd.dal.dataobject.stock.RdStockTransactionDO;
import cn.iocoder.yudao.module.rd.dal.mysql.stock.RdStockLotMapper;
import cn.iocoder.yudao.module.rd.dal.mysql.stock.RdStockTransactionMapper;
import cn.iocoder.yudao.module.rd.enums.RdStockLotStatusEnum;
import cn.iocoder.yudao.module.rd.enums.RdStockTransactionTypeEnum;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.math.BigDecimal;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.STOCK_LOT_NOT_EXISTS;

/**
 * RD stock lot Service implementation.
 */
@Service
@Validated
public class RdStockLotServiceImpl implements RdStockLotService {

    private static final String SOURCE_TYPE_PRODUCTION_OUTPUT = "PRODUCTION_OUTPUT";

    @Resource
    private RdStockLotMapper stockLotMapper;
    @Resource
    private RdStockTransactionMapper stockTransactionMapper;

    @Override
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

        stockTransactionMapper.insert(new RdStockTransactionDO()
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
                .setRemark("生产产出转入库"));
        return stockLot;
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
