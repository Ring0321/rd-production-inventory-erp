package com.ruiding.erp.dal.mysql.stock;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.ruiding.erp.controller.admin.stock.vo.RdStockLotPageReqVO;
import com.ruiding.erp.controller.admin.order.vo.RdOrderStockCandidatePageReqVO;
import com.ruiding.erp.dal.dataobject.order.RdSaleOrderDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockLotDO;
import com.ruiding.erp.enums.RdOrderFulfillmentStatusEnum;
import com.ruiding.erp.enums.RdStockLotStatusEnum;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

import java.util.Arrays;

/**
 * RD stock lot Mapper.
 */
@Mapper
public interface RdStockLotMapper extends BaseMapperX<RdStockLotDO> {

    @Select("SELECT * FROM rd_stock_lot WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    RdStockLotDO selectByIdForUpdate(Long id);

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM rd_stock_lot WHERE tenant_id = #{tenantId} AND location_code = #{locationCode} "
            + "AND customer_name = #{customerName} AND product_model = #{productModel} "
            + "AND furnace_no = #{furnaceNo} AND furnace_layer = #{furnaceLayer} AND deleted = 0 LIMIT 1 FOR UPDATE")
    RdStockLotDO selectInboundLotForUpdate(@Param("tenantId") Long tenantId,
                                           @Param("locationCode") String locationCode,
                                           @Param("customerName") String customerName,
                                           @Param("productModel") String productModel,
                                           @Param("furnaceNo") String furnaceNo,
                                           @Param("furnaceLayer") String furnaceLayer);

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
                .eqIfPresent(RdStockLotDO::getLocationId, reqVO.getLocationId())
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
