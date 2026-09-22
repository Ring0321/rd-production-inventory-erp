package com.ruiding.erp.dal.mysql.loading;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingExceptionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RdLoadingExceptionMapper extends BaseMapperX<RdLoadingExceptionDO> {
    @Select("SELECT * FROM rd_loading_exception WHERE task_id = #{taskId} "
            + "AND exception_status = 'OPEN' AND deleted = 0 FOR UPDATE")
    List<RdLoadingExceptionDO> selectOpenListByTaskIdForUpdate(Long taskId);
}
