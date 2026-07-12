package com.ruiding.erp.dal.mysql.outbound;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.ruiding.erp.dal.dataobject.outbound.RdOutboundOrderLineDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RdOutboundOrderLineMapper extends BaseMapperX<RdOutboundOrderLineDO> {
    default List<RdOutboundOrderLineDO> selectListByOrderId(Long orderId) {
        return selectList(new LambdaQueryWrapperX<RdOutboundOrderLineDO>()
                .eq(RdOutboundOrderLineDO::getOrderId, orderId)
                .orderByAsc(RdOutboundOrderLineDO::getId));
    }

    default RdOutboundOrderLineDO selectByOrderIdAndPackageId(Long orderId, Long packageId) {
        return selectOne(new LambdaQueryWrapperX<RdOutboundOrderLineDO>()
                .eq(RdOutboundOrderLineDO::getOrderId, orderId)
                .eq(RdOutboundOrderLineDO::getPackageId, packageId));
    }
}
