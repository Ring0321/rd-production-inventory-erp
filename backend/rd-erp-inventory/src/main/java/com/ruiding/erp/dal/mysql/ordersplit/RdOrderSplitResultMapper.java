package com.ruiding.erp.dal.mysql.ordersplit;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.ruiding.erp.controller.admin.ordersplit.vo.RdOrderSplitPageReqVO;
import com.ruiding.erp.dal.dataobject.ordersplit.RdOrderSplitResultDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RdOrderSplitResultMapper extends BaseMapperX<RdOrderSplitResultDO> {
    @Select("SELECT * FROM rd_order_split_result WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    RdOrderSplitResultDO selectByIdForUpdate(Long id);

    default PageResult<RdOrderSplitResultDO> selectPage(RdOrderSplitPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<RdOrderSplitResultDO>()
                .likeIfPresent(RdOrderSplitResultDO::getSplitNo, reqVO.getSplitNo())
                .eqIfPresent(RdOrderSplitResultDO::getSaleOrderId, reqVO.getSaleOrderId())
                .likeIfPresent(RdOrderSplitResultDO::getSaleOrderNo, reqVO.getSaleOrderNo())
                .likeIfPresent(RdOrderSplitResultDO::getCustomerNameSnapshot, reqVO.getCustomerName())
                .likeIfPresent(RdOrderSplitResultDO::getProductModelSnapshot, reqVO.getProductModel())
                .eqIfPresent(RdOrderSplitResultDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(RdOrderSplitResultDO::getSplitDate, reqVO.getSplitDate())
                .orderByDesc(RdOrderSplitResultDO::getSplitDate).orderByDesc(RdOrderSplitResultDO::getId));
    }
}
