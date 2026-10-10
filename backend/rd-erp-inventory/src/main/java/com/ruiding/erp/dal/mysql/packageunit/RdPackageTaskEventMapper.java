package com.ruiding.erp.dal.mysql.packageunit;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageTaskEventDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RdPackageTaskEventMapper extends BaseMapperX<RdPackageTaskEventDO> {
    default boolean hasSourceSnapshots(String sourceType, Long sourceId) {
        return selectCount(new LambdaQueryWrapperX<RdPackageTaskEventDO>()
                .eq(RdPackageTaskEventDO::getSourceType, sourceType)
                .eq(RdPackageTaskEventDO::getSourceId, sourceId)) > 0;
    }

    default List<RdPackageTaskEventDO> selectByPackageId(Long packageId) {
        return selectList(new LambdaQueryWrapperX<RdPackageTaskEventDO>()
                .eq(RdPackageTaskEventDO::getPackageId, packageId)
                .orderByAsc(RdPackageTaskEventDO::getEventTime, RdPackageTaskEventDO::getId));
    }
}
