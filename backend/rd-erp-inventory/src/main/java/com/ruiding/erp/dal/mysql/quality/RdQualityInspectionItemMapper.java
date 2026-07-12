package com.ruiding.erp.dal.mysql.quality;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionItemDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
@Mapper
public interface RdQualityInspectionItemMapper extends BaseMapperX<RdQualityInspectionItemDO> {
    default List<RdQualityInspectionItemDO> selectListByInspectionId(Long inspectionId) {
        return selectList(new LambdaQueryWrapperX<RdQualityInspectionItemDO>()
                .eq(RdQualityInspectionItemDO::getInspectionId, inspectionId)
                .orderByAsc(RdQualityInspectionItemDO::getId));
    }
}
