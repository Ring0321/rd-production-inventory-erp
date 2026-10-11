package com.ruiding.erp.dal.mysql.quality;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionEventDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface RdQualityInspectionEventMapper extends BaseMapperX<RdQualityInspectionEventDO> {
    default List<RdQualityInspectionEventDO> selectListByInspectionId(Long inspectionId) {
        return selectList(new LambdaQueryWrapperX<RdQualityInspectionEventDO>()
                .eq(RdQualityInspectionEventDO::getInspectionId, inspectionId)
                .orderByAsc(RdQualityInspectionEventDO::getId));
    }
}
