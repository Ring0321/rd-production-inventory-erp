package cn.iocoder.yudao.module.rd.dal.mysql.order;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rd.controller.admin.order.vo.RdOrderFulfillmentPageReqVO;
import cn.iocoder.yudao.module.rd.dal.dataobject.order.RdOrderFulfillmentDO;
import cn.iocoder.yudao.module.rd.enums.RdOrderFulfillmentStatusEnum;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * RD order fulfillment Mapper.
 */
@Mapper
public interface RdOrderFulfillmentMapper extends BaseMapperX<RdOrderFulfillmentDO> {

    @Select("SELECT * FROM rd_order_fulfillment WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    RdOrderFulfillmentDO selectByIdForUpdate(Long id);

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
