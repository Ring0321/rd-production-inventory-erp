package cn.iocoder.yudao.module.rd.dal.mysql.stock;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rd.controller.admin.stock.vo.RdStockLotPageReqVO;
import cn.iocoder.yudao.module.rd.dal.dataobject.stock.RdStockLotDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * RD stock lot Mapper.
 */
@Mapper
public interface RdStockLotMapper extends BaseMapperX<RdStockLotDO> {

    default PageResult<RdStockLotDO> selectPage(RdStockLotPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<RdStockLotDO>()
                .likeIfPresent(RdStockLotDO::getLocationCode, reqVO.getLocationCode())
                .likeIfPresent(RdStockLotDO::getCustomerName, reqVO.getCustomerName())
                .likeIfPresent(RdStockLotDO::getProductModel, reqVO.getProductModel())
                .likeIfPresent(RdStockLotDO::getFurnaceNo, reqVO.getFurnaceNo())
                .likeIfPresent(RdStockLotDO::getFurnaceLayer, reqVO.getFurnaceLayer())
                .eqIfPresent(RdStockLotDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(RdStockLotDO::getLastBizDate, reqVO.getLastBizDate())
                .orderByDesc(RdStockLotDO::getLastBizDate)
                .orderByDesc(RdStockLotDO::getId));
    }

}
