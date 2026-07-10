package cn.iocoder.yudao.module.rd.dal.mysql.stock;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rd.controller.admin.stock.vo.RdStockTransactionPageReqVO;
import cn.iocoder.yudao.module.rd.dal.dataobject.stock.RdStockTransactionDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * RD stock transaction Mapper.
 */
@Mapper
public interface RdStockTransactionMapper extends BaseMapperX<RdStockTransactionDO> {

    default PageResult<RdStockTransactionDO> selectPage(RdStockTransactionPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<RdStockTransactionDO>()
                .eqIfPresent(RdStockTransactionDO::getStockLotId, reqVO.getStockLotId())
                .betweenIfPresent(RdStockTransactionDO::getBizDate, reqVO.getBizDate())
                .eqIfPresent(RdStockTransactionDO::getTransactionType, reqVO.getTransactionType())
                .likeIfPresent(RdStockTransactionDO::getLocationCode, reqVO.getLocationCode())
                .likeIfPresent(RdStockTransactionDO::getCustomerName, reqVO.getCustomerName())
                .likeIfPresent(RdStockTransactionDO::getProductModel, reqVO.getProductModel())
                .likeIfPresent(RdStockTransactionDO::getFurnaceNo, reqVO.getFurnaceNo())
                .likeIfPresent(RdStockTransactionDO::getFurnaceLayer, reqVO.getFurnaceLayer())
                .eqIfPresent(RdStockTransactionDO::getSourceType, reqVO.getSourceType())
                .likeIfPresent(RdStockTransactionDO::getSourceNo, reqVO.getSourceNo())
                .eqIfPresent(RdStockTransactionDO::getRelatedOrderId, reqVO.getRelatedOrderId())
                .orderByDesc(RdStockTransactionDO::getBizDate)
                .orderByDesc(RdStockTransactionDO::getId));
    }

}
