package com.ruiding.erp.dal.mysql.loading;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import com.ruiding.erp.dal.dataobject.loading.RdPickupSlipLineDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RdPickupSlipLineMapper extends BaseMapperX<RdPickupSlipLineDO> {
    default List<RdPickupSlipLineDO> selectListBySlipId(Long slipId) {
        return selectList(RdPickupSlipLineDO::getSlipId, slipId);
    }
}
