package com.ruiding.erp.dal.mysql.loading;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import com.ruiding.erp.dal.dataobject.loading.RdPickupSlipDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RdPickupSlipMapper extends BaseMapperX<RdPickupSlipDO> {
    @Select("SELECT * FROM rd_pickup_slip WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    RdPickupSlipDO selectByIdForUpdate(Long id);
}
