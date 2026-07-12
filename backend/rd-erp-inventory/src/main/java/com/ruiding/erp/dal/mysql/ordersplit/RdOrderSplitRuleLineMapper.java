package com.ruiding.erp.dal.mysql.ordersplit;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import com.ruiding.erp.dal.dataobject.ordersplit.RdOrderSplitRuleLineDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RdOrderSplitRuleLineMapper extends BaseMapperX<RdOrderSplitRuleLineDO> {
    @Select("SELECT * FROM rd_order_split_rule_line WHERE rule_id = #{ruleId} AND deleted = 0 ORDER BY sequence_no, id")
    List<RdOrderSplitRuleLineDO> selectByRuleId(Long ruleId);

    @Delete("DELETE FROM rd_order_split_rule_line WHERE rule_id = #{ruleId}")
    int deleteByRuleId(Long ruleId);
}
