package com.ruiding.erp.dal.mysql.putaway;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayTaskLineDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RdPutawayTaskLineMapper extends BaseMapperX<RdPutawayTaskLineDO> {
    @Select("SELECT * FROM rd_putaway_task_line WHERE task_id = #{taskId} AND deleted = 0 FOR UPDATE")
    List<RdPutawayTaskLineDO> selectListByTaskIdForUpdate(Long taskId);

    @Select("SELECT l.* FROM rd_putaway_task_line l "
            + "INNER JOIN rd_putaway_task t ON t.id = l.task_id AND t.tenant_id = l.tenant_id AND t.deleted = 0 "
            + "WHERE l.package_code = #{packageCode} AND l.deleted = 0 "
            + "AND t.status IN ('ASSIGNED','MOVING','PENDING_CONFIRMATION','EXCEPTION_PENDING') "
            + "ORDER BY t.id DESC LIMIT 1")
    RdPutawayTaskLineDO selectActiveByPackageCode(String packageCode);

    default List<RdPutawayTaskLineDO> selectListByTaskId(Long taskId) {
        return selectList(RdPutawayTaskLineDO::getTaskId, taskId);
    }
}
