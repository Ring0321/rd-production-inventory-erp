package com.ruiding.erp.dal.mysql.putaway;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayExceptionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RdPutawayExceptionMapper extends BaseMapperX<RdPutawayExceptionDO> {
    @Select("SELECT * FROM rd_putaway_exception WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    RdPutawayExceptionDO selectByIdForUpdate(Long id);
}
