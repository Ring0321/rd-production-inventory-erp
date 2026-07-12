package com.ruiding.erp.dal.mysql.ordersplit;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.ruiding.erp.controller.admin.ordersplit.vo.RdOrderSplitRulePageReqVO;
import com.ruiding.erp.dal.dataobject.ordersplit.RdOrderSplitRuleDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RdOrderSplitRuleMapper extends BaseMapperX<RdOrderSplitRuleDO> {
    @Select("SELECT * FROM rd_order_split_rule WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    RdOrderSplitRuleDO selectByIdForUpdate(Long id);

    default PageResult<RdOrderSplitRuleDO> selectPage(RdOrderSplitRulePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<RdOrderSplitRuleDO>()
                .likeIfPresent(RdOrderSplitRuleDO::getRuleCode, reqVO.getRuleCode())
                .likeIfPresent(RdOrderSplitRuleDO::getRuleName, reqVO.getRuleName())
                .likeIfPresent(RdOrderSplitRuleDO::getCustomerName, reqVO.getCustomerName())
                .likeIfPresent(RdOrderSplitRuleDO::getProductModel, reqVO.getProductModel())
                .eqIfPresent(RdOrderSplitRuleDO::getEnabled, reqVO.getEnabled())
                .orderByDesc(RdOrderSplitRuleDO::getId));
    }
}
