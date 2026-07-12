package com.ruiding.erp.dal.mysql.outbound;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.ruiding.erp.controller.admin.outbound.vo.RdOutboundOrderPageReqVO;
import com.ruiding.erp.dal.dataobject.outbound.RdOutboundOrderDO;
import com.ruiding.erp.enums.RdOutboundOrderStatusEnum;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RdOutboundOrderMapper extends BaseMapperX<RdOutboundOrderDO> {

    @Select("SELECT * FROM rd_outbound_order WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    RdOutboundOrderDO selectByIdForUpdate(Long id);

    default RdOutboundOrderDO selectByOutboundNo(String outboundNo) {
        return selectOne(RdOutboundOrderDO::getOutboundNo, outboundNo);
    }

    default RdOutboundOrderDO selectDraftByFulfillmentId(Long fulfillmentId) {
        return selectOne(new LambdaQueryWrapperX<RdOutboundOrderDO>()
                .eq(RdOutboundOrderDO::getFulfillmentId, fulfillmentId)
                .in(RdOutboundOrderDO::getStatus,
                        RdOutboundOrderStatusEnum.DRAFT.getStatus(),
                        RdOutboundOrderStatusEnum.SCANNING.getStatus(),
                        RdOutboundOrderStatusEnum.SUBMITTED.getStatus(),
                        RdOutboundOrderStatusEnum.APPROVED.getStatus()));
    }

    default PageResult<RdOutboundOrderDO> selectPage(RdOutboundOrderPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<RdOutboundOrderDO>()
                .likeIfPresent(RdOutboundOrderDO::getOutboundNo, reqVO.getOutboundNo())
                .eqIfPresent(RdOutboundOrderDO::getOrderId, reqVO.getOrderId())
                .eqIfPresent(RdOutboundOrderDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(RdOutboundOrderDO::getOutboundDate, reqVO.getOutboundDate())
                .orderByDesc(RdOutboundOrderDO::getOutboundDate)
                .orderByDesc(RdOutboundOrderDO::getId));
    }

}
