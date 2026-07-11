package com.ruiding.erp.dal.mysql.order;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.ruiding.erp.controller.admin.order.vo.RdOrderFulfillmentPageReqVO;
import com.ruiding.erp.dal.dataobject.order.RdOrderFulfillmentDO;
import com.ruiding.erp.enums.RdOrderFulfillmentStatusEnum;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * RD order fulfillment Mapper.
 */
@Mapper
public interface RdOrderFulfillmentMapper extends BaseMapperX<RdOrderFulfillmentDO> {

    @Select("SELECT * FROM rd_order_fulfillment WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    RdOrderFulfillmentDO selectByIdForUpdate(Long id);

    @Select("SELECT COALESCE(SUM(outbound_qty), 0) FROM rd_order_fulfillment "
            + "WHERE order_id = #{orderId} AND deleted = 0 AND status <> 'CANCELLED'")
    BigDecimal sumOutboundQtyByOrderId(Long orderId);

    default RdOrderFulfillmentDO selectActive(Long orderId, Long stockLotId) {
        return selectOne(new LambdaQueryWrapperX<RdOrderFulfillmentDO>()
                .eq(RdOrderFulfillmentDO::getOrderId, orderId)
                .eq(RdOrderFulfillmentDO::getStockLotId, stockLotId)
                .ne(RdOrderFulfillmentDO::getStatus, RdOrderFulfillmentStatusEnum.CANCELLED.getStatus()));
    }

    default PageResult<RdOrderFulfillmentDO> selectPage(RdOrderFulfillmentPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<RdOrderFulfillmentDO>()
                .eqIfPresent(RdOrderFulfillmentDO::getOrderId, reqVO.getOrderId())
                .eqIfPresent(RdOrderFulfillmentDO::getStockLotId, reqVO.getStockLotId())
                .eqIfPresent(RdOrderFulfillmentDO::getStatus, reqVO.getStatus())
                .orderByDesc(RdOrderFulfillmentDO::getId));
    }

}
