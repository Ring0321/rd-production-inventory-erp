package cn.iocoder.yudao.module.rd.dal.mysql.production;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rd.controller.admin.production.vo.RdProductionOutputPageReqVO;
import cn.iocoder.yudao.module.rd.dal.dataobject.production.RdProductionOutputDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * RD furnace-layer production output Mapper.
 */
@Mapper
public interface RdProductionOutputMapper extends BaseMapperX<RdProductionOutputDO> {

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
