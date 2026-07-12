package com.ruiding.erp.dal.mysql.stocktake;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.ruiding.erp.dal.dataobject.stocktake.RdStocktakeOrderLineDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RdStocktakeOrderLineMapper extends BaseMapperX<RdStocktakeOrderLineDO> {

    default List<RdStocktakeOrderLineDO> selectListByOrderId(Long orderId) {
        return selectList(new LambdaQueryWrapperX<RdStocktakeOrderLineDO>()
                .eq(RdStocktakeOrderLineDO::getOrderId, orderId)
                .orderByAsc(RdStocktakeOrderLineDO::getId));
    }

    default RdStocktakeOrderLineDO selectByOrderIdAndPackageId(Long orderId, Long packageId) {
        return selectOne(new LambdaQueryWrapperX<RdStocktakeOrderLineDO>()
                .eq(RdStocktakeOrderLineDO::getOrderId, orderId)
                .eq(RdStocktakeOrderLineDO::getPackageId, packageId));
    }
}
