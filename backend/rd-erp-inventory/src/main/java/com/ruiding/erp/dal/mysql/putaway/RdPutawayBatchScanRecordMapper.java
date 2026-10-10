package com.ruiding.erp.dal.mysql.putaway;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayBatchScanRecordDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RdPutawayBatchScanRecordMapper extends BaseMapperX<RdPutawayBatchScanRecordDO> {
    default RdPutawayBatchScanRecordDO selectByTaskAndKey(Long taskId, String key) {
        return selectOne(new LambdaQueryWrapperX<RdPutawayBatchScanRecordDO>()
                .eq(RdPutawayBatchScanRecordDO::getTaskId, taskId)
                .eq(RdPutawayBatchScanRecordDO::getIdempotencyKey, key));
    }
}
