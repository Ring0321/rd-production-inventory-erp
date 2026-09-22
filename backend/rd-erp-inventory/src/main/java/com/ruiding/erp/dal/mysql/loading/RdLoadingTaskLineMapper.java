package com.ruiding.erp.dal.mysql.loading;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingTaskLineDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RdLoadingTaskLineMapper extends BaseMapperX<RdLoadingTaskLineDO> {
    @Select("SELECT * FROM rd_loading_task_line WHERE task_id = #{taskId} AND deleted = 0 FOR UPDATE")
    List<RdLoadingTaskLineDO> selectListByTaskIdForUpdate(Long taskId);

    default List<RdLoadingTaskLineDO> selectListByTaskId(Long taskId) {
        return selectList(RdLoadingTaskLineDO::getTaskId, taskId);
    }
}
