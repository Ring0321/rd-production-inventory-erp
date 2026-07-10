package cn.iocoder.yudao.module.rd.dal.mysql.outbound;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rd.controller.admin.outbound.vo.RdOutboundOrderPageReqVO;
import cn.iocoder.yudao.module.rd.dal.dataobject.outbound.RdOutboundOrderDO;
import cn.iocoder.yudao.module.rd.enums.RdOutboundOrderStatusEnum;
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
                .eq(RdOutboundOrderDO::getStatus, RdOutboundOrderStatusEnum.DRAFT.getStatus()));
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
