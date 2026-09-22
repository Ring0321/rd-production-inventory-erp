package com.ruiding.erp.dal.mysql.loading;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingPackageClaimDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RdLoadingPackageClaimMapper extends BaseMapperX<RdLoadingPackageClaimDO> {
    @Select("SELECT * FROM rd_loading_package_claim WHERE package_id = #{packageId} FOR UPDATE")
    RdLoadingPackageClaimDO selectByPackageIdForUpdate(Long packageId);

    @Select("SELECT * FROM rd_loading_package_claim WHERE task_id = #{taskId} FOR UPDATE")
    List<RdLoadingPackageClaimDO> selectListByTaskIdForUpdate(Long taskId);
}
