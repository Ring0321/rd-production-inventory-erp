package com.ruiding.erp.dal.mysql.stocktake;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.ruiding.erp.controller.admin.stocktake.vo.RdStocktakePageReqVO;
import com.ruiding.erp.dal.dataobject.stocktake.RdStocktakeOrderDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RdStocktakeOrderMapper extends BaseMapperX<RdStocktakeOrderDO> {

    @Select("SELECT * FROM rd_stocktake_order WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    RdStocktakeOrderDO selectByIdForUpdate(Long id);

    default PageResult<RdStocktakeOrderDO> selectPage(RdStocktakePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<RdStocktakeOrderDO>()
                .likeIfPresent(RdStocktakeOrderDO::getStocktakeNo, reqVO.getStocktakeNo())
                .eqIfPresent(RdStocktakeOrderDO::getLocationId, reqVO.getLocationId())
                .likeIfPresent(RdStocktakeOrderDO::getLocationCode, reqVO.getLocationCode())
                .eqIfPresent(RdStocktakeOrderDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(RdStocktakeOrderDO::getStocktakeDate, reqVO.getStocktakeDate())
                .orderByDesc(RdStocktakeOrderDO::getStocktakeDate)
                .orderByDesc(RdStocktakeOrderDO::getId));
    }
}
