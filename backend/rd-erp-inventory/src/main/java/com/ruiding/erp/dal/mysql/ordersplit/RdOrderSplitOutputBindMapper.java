package com.ruiding.erp.dal.mysql.ordersplit;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import com.ruiding.erp.dal.dataobject.ordersplit.RdOrderSplitOutputBindDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RdOrderSplitOutputBindMapper extends BaseMapperX<RdOrderSplitOutputBindDO> {
    @Select("SELECT * FROM rd_order_split_output_bind WHERE production_output_id = #{productionOutputId} AND deleted = 0 FOR UPDATE")
    RdOrderSplitOutputBindDO selectByProductionOutputIdForUpdate(Long productionOutputId);

    @Select("SELECT * FROM rd_order_split_output_bind WHERE production_output_id = #{productionOutputId} AND status = 'ACTIVE' AND deleted = 0 LIMIT 1")
    RdOrderSplitOutputBindDO selectActiveByProductionOutputId(Long productionOutputId);

    @Select("SELECT * FROM rd_order_split_output_bind WHERE sale_order_id = #{orderId} AND status = 'ACTIVE' AND deleted = 0")
    List<RdOrderSplitOutputBindDO> selectActiveByOrderId(Long orderId);

    @Select("SELECT * FROM rd_order_split_output_bind WHERE result_id = #{resultId} AND deleted = 0 ORDER BY id")
    List<RdOrderSplitOutputBindDO> selectByResultId(Long resultId);

    @Select("SELECT COALESCE(SUM(output_qty_snapshot), 0) FROM rd_order_split_output_bind WHERE result_line_id = #{lineId} AND status = 'ACTIVE' AND deleted = 0")
    java.math.BigDecimal sumActiveQtyByResultLineId(Long lineId);

    @Select({"<script>",
            "SELECT result_id, COALESCE(SUM(output_qty_snapshot), 0) AS output_qty_snapshot",
            "FROM rd_order_split_output_bind WHERE status = 'ACTIVE' AND deleted = 0 AND result_id IN",
            "<foreach collection='resultIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "GROUP BY result_id",
            "</script>"})
    List<RdOrderSplitOutputBindDO> selectActiveQtyByResultIds(@Param("resultIds") List<Long> resultIds);
}
