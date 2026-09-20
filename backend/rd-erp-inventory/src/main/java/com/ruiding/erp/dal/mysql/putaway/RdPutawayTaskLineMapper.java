package com.ruiding.erp.dal.mysql.putaway;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayTaskLineDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RdPutawayTaskLineMapper extends BaseMapperX<RdPutawayTaskLineDO> {
    @Select("SELECT * FROM rd_putaway_task_line WHERE task_id = #{taskId} AND deleted = 0 "
            + "ORDER BY line_no FOR UPDATE")
    List<RdPutawayTaskLineDO> selectListByTaskIdForUpdate(Long taskId);
}
