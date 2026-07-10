package cn.iocoder.yudao.module.rd.dal.mysql.order;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rd.controller.admin.order.vo.RdSaleOrderPageReqVO;
import cn.iocoder.yudao.module.rd.dal.dataobject.order.RdSaleOrderDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * RD sales order Mapper.
 */
@Mapper
public interface RdSaleOrderMapper extends BaseMapperX<RdSaleOrderDO> {

    default RdSaleOrderDO selectByOrderNo(String orderNo) {
        return selectOne(RdSaleOrderDO::getOrderNo, orderNo);
    }

    default PageResult<RdSaleOrderDO> selectPage(RdSaleOrderPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<RdSaleOrderDO>()
                .likeIfPresent(RdSaleOrderDO::getOrderNo, reqVO.getOrderNo())
                .likeIfPresent(RdSaleOrderDO::getCustomerName, reqVO.getCustomerName())
                .likeIfPresent(RdSaleOrderDO::getProductModel, reqVO.getProductModel())
                .eqIfPresent(RdSaleOrderDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(RdSaleOrderDO::getOrderDate, reqVO.getOrderDate())
                .orderByDesc(RdSaleOrderDO::getOrderDate)
                .orderByDesc(RdSaleOrderDO::getId));
    }

}
