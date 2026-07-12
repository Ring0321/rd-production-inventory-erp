package com.ruiding.erp.dal.mysql.stock;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.ruiding.erp.controller.admin.stock.vo.RdStockTransactionPageReqVO;
import com.ruiding.erp.dal.dataobject.stock.RdStockTransactionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * RD stock transaction Mapper.
 */
@Mapper
public interface RdStockTransactionMapper extends BaseMapperX<RdStockTransactionDO> {

    @Select("SELECT * FROM rd_stock_transaction WHERE idempotency_key = #{idempotencyKey} AND deleted = 0 LIMIT 1")
    RdStockTransactionDO selectByIdempotencyKey(String idempotencyKey);

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
