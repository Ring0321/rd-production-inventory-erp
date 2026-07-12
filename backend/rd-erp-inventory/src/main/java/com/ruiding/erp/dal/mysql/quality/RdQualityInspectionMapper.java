package com.ruiding.erp.dal.mysql.quality;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
@Mapper
public interface RdQualityInspectionMapper extends BaseMapperX<RdQualityInspectionDO> {
    @Select("SELECT * FROM rd_quality_inspection WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    RdQualityInspectionDO selectByIdForUpdate(Long id);
}
