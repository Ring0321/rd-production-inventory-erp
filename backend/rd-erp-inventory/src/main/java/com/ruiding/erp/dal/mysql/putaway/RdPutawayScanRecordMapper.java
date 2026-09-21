package com.ruiding.erp.dal.mysql.putaway;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayScanRecordDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RdPutawayScanRecordMapper extends BaseMapperX<RdPutawayScanRecordDO> {
    default RdPutawayScanRecordDO selectByTaskAndIdempotencyKey(Long taskId,
                                                                String idempotencyKey) {
        return selectOne(new LambdaQueryWrapperX<RdPutawayScanRecordDO>()
                .eq(RdPutawayScanRecordDO::getTaskId, taskId)
                .eq(RdPutawayScanRecordDO::getIdempotencyKey, idempotencyKey));
    }
}
