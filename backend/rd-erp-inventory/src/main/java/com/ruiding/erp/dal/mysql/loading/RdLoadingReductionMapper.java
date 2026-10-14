package com.ruiding.erp.dal.mysql.loading;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingReductionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RdLoadingReductionMapper extends BaseMapperX<RdLoadingReductionDO> {
    @Select("SELECT * FROM rd_loading_reduction WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0 FOR UPDATE")
    RdLoadingReductionDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Select("SELECT * FROM rd_loading_reduction WHERE task_id = #{taskId} AND tenant_id = #{tenantId} AND deleted = 0 ORDER BY id DESC LIMIT 100")
    List<RdLoadingReductionDO> selectTaskList(@Param("taskId") Long taskId, @Param("tenantId") Long tenantId);

    @Select("SELECT * FROM rd_loading_reduction WHERE pickup_slip_id = #{slipId} AND tenant_id = #{tenantId} AND deleted = 0 ORDER BY id DESC LIMIT 100")
    List<RdLoadingReductionDO> selectSlipList(@Param("slipId") Long slipId, @Param("tenantId") Long tenantId);

    @Select("SELECT * FROM rd_loading_reduction WHERE task_id = #{taskId} AND tenant_id = #{tenantId} AND deleted = 0 FOR UPDATE")
    List<RdLoadingReductionDO> selectTaskListForUpdate(@Param("taskId") Long taskId, @Param("tenantId") Long tenantId);

    @Select("SELECT * FROM rd_loading_reduction WHERE task_id = #{taskId} AND tenant_id = #{tenantId} AND idempotency_key = #{key} AND deleted = 0 FOR UPDATE")
    RdLoadingReductionDO selectByKeyForUpdate(@Param("taskId") Long taskId, @Param("tenantId") Long tenantId, @Param("key") String key);
    @Select("SELECT * FROM rd_loading_reduction WHERE task_id = #{taskId} AND tenant_id = #{tenantId} AND idempotency_key = #{key} AND deleted = 0")
    RdLoadingReductionDO selectByKey(@Param("taskId") Long taskId, @Param("tenantId") Long tenantId, @Param("key") String key);
}
