package cn.iocoder.yudao.module.rd.dal.mysql.stock;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rd.controller.admin.stock.vo.RdStockLotPageReqVO;
import cn.iocoder.yudao.module.rd.controller.admin.order.vo.RdOrderStockCandidatePageReqVO;
import cn.iocoder.yudao.module.rd.dal.dataobject.order.RdSaleOrderDO;
import cn.iocoder.yudao.module.rd.dal.dataobject.stock.RdStockLotDO;
import cn.iocoder.yudao.module.rd.enums.RdOrderFulfillmentStatusEnum;
import cn.iocoder.yudao.module.rd.enums.RdStockLotStatusEnum;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Arrays;

/**
 * RD stock lot Mapper.
 */
@Mapper
public interface RdStockLotMapper extends BaseMapperX<RdStockLotDO> {

    @Select("SELECT * FROM rd_stock_lot WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    RdStockLotDO selectByIdForUpdate(Long id);

    default PageResult<RdStockLotDO> selectMatchCandidatePage(RdOrderStockCandidatePageReqVO reqVO,
                                                              RdSaleOrderDO saleOrder) {
        LambdaQueryWrapperX<RdStockLotDO> query = new LambdaQueryWrapperX<>();
        query.eq(RdStockLotDO::getProductModel, saleOrder.getProductModel());
        query.in(RdStockLotDO::getStatus, Arrays.asList(
                RdStockLotStatusEnum.AVAILABLE.getStatus(), RdStockLotStatusEnum.LOCKED.getStatus()));
        query.apply("warehouse_qty > COALESCE(locked_qty, 0)");
        query.notExists("SELECT 1 FROM rd_order_fulfillment f WHERE f.deleted = 0 "
                        + "AND f.status <> {0} AND f.order_id = {1} "
                        + "AND f.stock_lot_id = rd_stock_lot.id",
                RdOrderFulfillmentStatusEnum.CANCELLED.getStatus(), reqVO.getOrderId());
        query.orderByAsc(RdStockLotDO::getLastBizDate);
        query.orderByAsc(RdStockLotDO::getId);
        if (!Boolean.TRUE.equals(reqVO.getIncludeCrossCustomer())) {
            query.eq(RdStockLotDO::getCustomerName, saleOrder.getCustomerName());
        }
        return selectPage(reqVO, query);
    }

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
