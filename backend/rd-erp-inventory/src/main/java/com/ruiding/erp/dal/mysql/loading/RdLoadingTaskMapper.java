package com.ruiding.erp.dal.mysql.loading;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RdLoadingTaskMapper extends BaseMapperX<RdLoadingTaskDO> {
    @Select("SELECT * FROM rd_loading_task WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    RdLoadingTaskDO selectByIdForUpdate(Long id);
}
