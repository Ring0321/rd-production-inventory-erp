package com.ruiding.erp.dal.mysql.putaway;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayExceptionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RdPutawayExceptionMapper extends BaseMapperX<RdPutawayExceptionDO> {
    @Select("SELECT * FROM rd_putaway_exception WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    RdPutawayExceptionDO selectByIdForUpdate(Long id);

    default List<RdPutawayExceptionDO> selectListByTaskId(Long taskId) {
        return selectList(new LambdaQueryWrapperX<RdPutawayExceptionDO>()
                .eq(RdPutawayExceptionDO::getTaskId, taskId)
                .orderByDesc(RdPutawayExceptionDO::getReportedTime)
                .orderByDesc(RdPutawayExceptionDO::getId));
    }

    @Select("SELECT * FROM rd_putaway_exception WHERE task_id = #{taskId} "
            + "AND exception_status = 'OPEN' AND deleted = 0 ORDER BY id FOR UPDATE")
    List<RdPutawayExceptionDO> selectOpenListByTaskIdForUpdate(Long taskId);
}
