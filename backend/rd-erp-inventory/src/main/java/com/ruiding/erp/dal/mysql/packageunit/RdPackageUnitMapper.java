package com.ruiding.erp.dal.mysql.packageunit;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface RdPackageUnitMapper extends BaseMapperX<RdPackageUnitDO> {
    @Select("SELECT * FROM rd_package_unit WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    RdPackageUnitDO selectByIdForUpdate(Long id);

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
}
