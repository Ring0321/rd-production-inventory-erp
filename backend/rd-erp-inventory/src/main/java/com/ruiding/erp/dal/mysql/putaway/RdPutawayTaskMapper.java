package com.ruiding.erp.dal.mysql.putaway;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RdPutawayTaskMapper extends BaseMapperX<RdPutawayTaskDO> {
    @Select("SELECT * FROM rd_putaway_task WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    RdPutawayTaskDO selectByIdForUpdate(Long id);
}
