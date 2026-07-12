package com.ruiding.erp.dal.mysql.ordersplit;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import com.ruiding.erp.dal.dataobject.ordersplit.RdOrderSplitResultLineDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RdOrderSplitResultLineMapper extends BaseMapperX<RdOrderSplitResultLineDO> {
    @Select("SELECT * FROM rd_order_split_result_line WHERE result_id = #{resultId} AND deleted = 0 ORDER BY sequence_no, id")
    List<RdOrderSplitResultLineDO> selectByResultId(Long resultId);

    @Select("SELECT * FROM rd_order_split_result_line WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    RdOrderSplitResultLineDO selectByIdForUpdate(Long id);
}
