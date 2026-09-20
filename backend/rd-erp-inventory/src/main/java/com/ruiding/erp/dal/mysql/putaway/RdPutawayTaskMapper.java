package com.ruiding.erp.dal.mysql.putaway;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import com.ruiding.erp.dal.dataobject.putaway.RdLocationCapacitySummaryDO;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface RdPutawayTaskMapper extends BaseMapperX<RdPutawayTaskDO> {
    @Select("SELECT * FROM rd_putaway_task WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    RdPutawayTaskDO selectByIdForUpdate(Long id);

    @Select("<script>SELECT planned_location_id AS locationId, SUM(package_count) AS packageCount, "
            + "COALESCE(SUM(total_weight), 0) AS totalWeight FROM rd_putaway_task "
            + "WHERE deleted = 0 AND planned_location_id IS NOT NULL AND status IN "
            + "<foreach collection='statuses' item='status' open='(' separator=',' close=')'>#{status}</foreach> "
            + "GROUP BY planned_location_id</script>")
    List<RdLocationCapacitySummaryDO> selectLocationReservationSummary(
            @Param("statuses") Collection<String> statuses);

    @Select("<script>SELECT #{locationId} AS locationId, COALESCE(SUM(package_count), 0) AS packageCount, "
            + "COALESCE(SUM(total_weight), 0) AS totalWeight FROM rd_putaway_task "
            + "WHERE deleted = 0 AND planned_location_id = #{locationId} AND status IN "
            + "<foreach collection='statuses' item='status' open='(' separator=',' close=')'>#{status}</foreach>"
            + "</script>")
    RdLocationCapacitySummaryDO selectLocationReservationSummaryByLocationId(
            @Param("locationId") Long locationId,
            @Param("statuses") Collection<String> statuses);
}
