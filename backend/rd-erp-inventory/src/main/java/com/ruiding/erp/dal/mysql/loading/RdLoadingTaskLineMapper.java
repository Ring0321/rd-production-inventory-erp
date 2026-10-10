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

    @Select("SELECT l.* FROM rd_loading_task_line l "
            + "INNER JOIN rd_loading_task t ON t.id = l.task_id AND t.tenant_id = l.tenant_id AND t.deleted = 0 "
            + "WHERE l.package_code = #{packageCode} AND l.deleted = 0 "
            + "AND t.status IN ('ASSIGNED','LOADING','PENDING_REVIEW','EXCEPTION_PENDING') "
            + "ORDER BY t.id DESC LIMIT 1")
    RdLoadingTaskLineDO selectActiveByPackageCode(String packageCode);

    default List<RdLoadingTaskLineDO> selectListByTaskId(Long taskId) {
        return selectList(RdLoadingTaskLineDO::getTaskId, taskId);
    }
}
