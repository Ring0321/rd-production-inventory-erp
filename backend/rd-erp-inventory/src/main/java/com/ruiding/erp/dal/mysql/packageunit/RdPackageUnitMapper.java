package com.ruiding.erp.dal.mysql.packageunit;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.putaway.RdLocationCapacitySummaryDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Mapper
public interface RdPackageUnitMapper extends BaseMapperX<RdPackageUnitDO> {
    @Select("SELECT * FROM rd_package_unit WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    RdPackageUnitDO selectByIdForUpdate(Long id);

    @Select("SELECT * FROM rd_package_unit WHERE package_code = #{packageCode} AND deleted = 0 FOR UPDATE")
    RdPackageUnitDO selectByPackageCodeForUpdate(String packageCode);

    @Select("SELECT * FROM rd_package_unit WHERE stock_lot_id = #{stockLotId} "
            + "AND status = #{status} AND deleted = 0 ORDER BY package_code LIMIT #{limit} FOR UPDATE")
    List<RdPackageUnitDO> selectListByStockLotIdForUpdate(@Param("stockLotId") Long stockLotId,
                                                          @Param("status") String status,
                                                          @Param("limit") Integer limit);

    default List<RdPackageUnitDO> selectListByProductionOutputId(Long productionOutputId) {
        return selectList(new LambdaQueryWrapperX<RdPackageUnitDO>()
                .eq(RdPackageUnitDO::getProductionOutputId, productionOutputId)
                .orderByAsc(RdPackageUnitDO::getPackageCode));
    }

    default long selectCountByProductionOutputId(Long productionOutputId) {
        return selectCount(RdPackageUnitDO::getProductionOutputId, productionOutputId);
    }

    default RdPackageUnitDO selectByPackageCode(String packageCode) {
        return selectOne(RdPackageUnitDO::getPackageCode, packageCode);
    }

    default List<RdPackageUnitDO> selectListByLocationIdAndStatuses(Long locationId, Collection<String> statuses) {
        return selectList(new LambdaQueryWrapperX<RdPackageUnitDO>()
                .eq(RdPackageUnitDO::getLocationId, locationId)
                .in(statuses != null && !statuses.isEmpty(), RdPackageUnitDO::getStatus, statuses)
                .orderByAsc(RdPackageUnitDO::getPackageCode));
    }

    @Insert("INSERT INTO rd_package_code_sequence "
            + "(tenant_id, biz_date, current_value, creator, create_time, updater, update_time, deleted) "
            + "VALUES (#{tenantId}, #{date}, #{count}, 'system', NOW(), 'system', NOW(), b'0') "
            + "ON DUPLICATE KEY UPDATE current_value = current_value + VALUES(current_value), "
            + "updater = 'system', update_time = NOW()")
    void incrementDailySequence(@Param("tenantId") Long tenantId, @Param("date") LocalDate date,
                                @Param("count") Integer count);

    @Select("SELECT current_value FROM rd_package_code_sequence "
            + "WHERE tenant_id = #{tenantId} AND biz_date = #{date} AND deleted = 0 FOR UPDATE")
    Integer selectCurrentDailySequenceForUpdate(@Param("tenantId") Long tenantId,
                                                @Param("date") LocalDate date);

    @Select("<script>SELECT location_id AS locationId, COUNT(*) AS packageCount, "
            + "COALESCE(SUM(package_weight), 0) AS totalWeight FROM rd_package_unit "
            + "WHERE deleted = 0 AND location_id IS NOT NULL AND status IN "
            + "<foreach collection='statuses' item='status' open='(' separator=',' close=')'>#{status}</foreach> "
            + "GROUP BY location_id</script>")
    List<RdLocationCapacitySummaryDO> selectLocationOccupancySummary(
            @Param("statuses") Collection<String> statuses);

    @Select("<script>SELECT #{locationId} AS locationId, COUNT(*) AS packageCount, "
            + "COALESCE(SUM(package_weight), 0) AS totalWeight FROM rd_package_unit "
            + "WHERE deleted = 0 AND location_id = #{locationId} AND status IN "
            + "<foreach collection='statuses' item='status' open='(' separator=',' close=')'>#{status}</foreach>"
            + "</script>")
    RdLocationCapacitySummaryDO selectLocationOccupancySummaryByLocationId(
            @Param("locationId") Long locationId,
            @Param("statuses") Collection<String> statuses);
}
