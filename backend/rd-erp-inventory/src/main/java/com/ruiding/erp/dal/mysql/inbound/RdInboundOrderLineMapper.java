package com.ruiding.erp.dal.mysql.inbound;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.ruiding.erp.dal.dataobject.inbound.RdInboundOrderLineDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
@Mapper
public interface RdInboundOrderLineMapper extends BaseMapperX<RdInboundOrderLineDO> {
    default List<RdInboundOrderLineDO> selectListByOrderId(Long orderId) {
        return selectList(new LambdaQueryWrapperX<RdInboundOrderLineDO>()
                .eq(RdInboundOrderLineDO::getOrderId, orderId).orderByAsc(RdInboundOrderLineDO::getId));
    }
}
