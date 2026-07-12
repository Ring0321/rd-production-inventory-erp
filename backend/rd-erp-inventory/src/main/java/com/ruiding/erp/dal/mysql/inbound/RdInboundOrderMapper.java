package com.ruiding.erp.dal.mysql.inbound;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import com.ruiding.erp.dal.dataobject.inbound.RdInboundOrderDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
@Mapper
public interface RdInboundOrderMapper extends BaseMapperX<RdInboundOrderDO> {
    @Select("SELECT * FROM rd_inbound_order WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    RdInboundOrderDO selectByIdForUpdate(Long id);
}
