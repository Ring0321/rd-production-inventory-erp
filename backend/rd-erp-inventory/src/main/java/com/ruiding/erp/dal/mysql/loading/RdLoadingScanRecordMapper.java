package com.ruiding.erp.dal.mysql.loading;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingScanRecordDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RdLoadingScanRecordMapper extends BaseMapperX<RdLoadingScanRecordDO> {
    default RdLoadingScanRecordDO selectByTaskAndIdempotencyKey(Long taskId, String idempotencyKey) {
        return selectOne(new LambdaQueryWrapperX<RdLoadingScanRecordDO>()
                .eq(RdLoadingScanRecordDO::getTaskId, taskId)
                .eq(RdLoadingScanRecordDO::getIdempotencyKey, idempotencyKey));
    }
}
