package com.ruiding.erp.dal.mysql.order;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.ruiding.erp.controller.admin.order.vo.RdSaleOrderPageReqVO;
import com.ruiding.erp.dal.dataobject.order.RdSaleOrderDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * RD sales order Mapper.
 */
@Mapper
public interface RdSaleOrderMapper extends BaseMapperX<RdSaleOrderDO> {

    @Select("SELECT * FROM rd_sale_order_extend WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    RdSaleOrderDO selectByIdForUpdate(Long id);

    @Update("UPDATE rd_sale_order_extend SET status = #{status}, close_time = NULL, close_by = NULL, "
            + "updater = #{operatorName}, update_time = NOW() WHERE id = #{id} AND deleted = 0")
    int reopenAfterOutboundCancel(@Param("id") Long id, @Param("status") String status,
                                  @Param("operatorName") String operatorName);

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
