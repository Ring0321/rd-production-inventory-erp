package com.ruiding.erp.dal.mysql.quality;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionMemberDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface RdQualityInspectionMemberMapper extends BaseMapperX<RdQualityInspectionMemberDO> {
    default List<RdQualityInspectionMemberDO> selectListByInspectionId(Long inspectionId) {
        return selectList(new LambdaQueryWrapperX<RdQualityInspectionMemberDO>()
                .eq(RdQualityInspectionMemberDO::getInspectionId, inspectionId)
                .orderByAsc(RdQualityInspectionMemberDO::getPackageId));
    }

    @Update("UPDATE rd_quality_inspection_member SET active_key = NULL "
            + "WHERE inspection_id = #{inspectionId} AND active_key = package_id AND deleted = 0")
    int releaseActiveByInspectionId(Long inspectionId);
}
