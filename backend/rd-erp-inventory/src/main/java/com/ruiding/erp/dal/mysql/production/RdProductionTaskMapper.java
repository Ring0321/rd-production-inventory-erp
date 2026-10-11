package com.ruiding.erp.dal.mysql.production;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.ruiding.erp.controller.admin.production.vo.RdProductionTaskPageReqVO;
import com.ruiding.erp.dal.dataobject.production.RdProductionTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RdProductionTaskMapper extends BaseMapperX<RdProductionTaskDO> {
    @Select("SELECT * FROM rd_production_task WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    RdProductionTaskDO selectByIdForUpdate(Long id);

    default PageResult<RdProductionTaskDO> selectTaskPage(RdProductionTaskPageReqVO req) {
        return selectPage(req, new LambdaQueryWrapperX<RdProductionTaskDO>()
                .likeIfPresent(RdProductionTaskDO::getTaskNo, req.getTaskNo())
                .likeIfPresent(RdProductionTaskDO::getCustomerName, req.getCustomerName())
                .eqIfPresent(RdProductionTaskDO::getStatus, req.getStatus())
                .orderByDesc(RdProductionTaskDO::getProductionDate, RdProductionTaskDO::getId));
    }
}
