package com.ruiding.erp.dal.mysql.production;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.ruiding.erp.controller.admin.production.vo.RdProductionOutputPageReqVO;
import com.ruiding.erp.dal.dataobject.production.RdProductionOutputDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * RD furnace-layer production output Mapper.
 */
@Mapper
public interface RdProductionOutputMapper extends BaseMapperX<RdProductionOutputDO> {

    @Select("SELECT * FROM rd_production_output WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    RdProductionOutputDO selectByIdForUpdate(Long id);

    default PageResult<RdProductionOutputDO> selectPage(RdProductionOutputPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<RdProductionOutputDO>()
                .betweenIfPresent(RdProductionOutputDO::getProductionDate, reqVO.getProductionDate())
                .likeIfPresent(RdProductionOutputDO::getLocationCode, reqVO.getLocationCode())
                .likeIfPresent(RdProductionOutputDO::getCustomerName, reqVO.getCustomerName())
                .likeIfPresent(RdProductionOutputDO::getProductModel, reqVO.getProductModel())
                .likeIfPresent(RdProductionOutputDO::getFurnaceNo, reqVO.getFurnaceNo())
                .likeIfPresent(RdProductionOutputDO::getFurnaceLayer, reqVO.getFurnaceLayer())
                .eqIfPresent(RdProductionOutputDO::getStatus, reqVO.getStatus())
                .orderByDesc(RdProductionOutputDO::getProductionDate)
                .orderByDesc(RdProductionOutputDO::getId));
    }

}
