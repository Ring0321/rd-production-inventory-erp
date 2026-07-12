package com.ruiding.erp.dal.mysql.masterdata;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import com.ruiding.erp.dal.dataobject.masterdata.RdWarehouseLocationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
@Mapper
public interface RdWarehouseLocationMapper extends BaseMapperX<RdWarehouseLocationDO> {

    @Select("SELECT * FROM rd_location WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    RdWarehouseLocationDO selectByIdForUpdate(Long id);
}
