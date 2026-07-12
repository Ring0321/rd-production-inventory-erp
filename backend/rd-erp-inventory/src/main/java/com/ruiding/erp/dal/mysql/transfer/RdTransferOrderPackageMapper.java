package com.ruiding.erp.dal.mysql.transfer;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.ruiding.erp.dal.dataobject.transfer.RdTransferOrderPackageDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RdTransferOrderPackageMapper extends BaseMapperX<RdTransferOrderPackageDO> {
    default List<RdTransferOrderPackageDO> selectListByTransferOrderId(Long transferOrderId) {
        return selectList(new LambdaQueryWrapperX<RdTransferOrderPackageDO>()
                .eq(RdTransferOrderPackageDO::getTransferOrderId, transferOrderId)
                .orderByAsc(RdTransferOrderPackageDO::getPackageCode));
    }
}
