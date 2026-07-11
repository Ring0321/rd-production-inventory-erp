package com.ruiding.erp.dal.mysql.report;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.ruiding.erp.controller.admin.report.vo.RdDailyStockSummaryPageReqVO;
import com.ruiding.erp.controller.admin.report.vo.RdDailyStockTrendRespVO;
import com.ruiding.erp.dal.dataobject.report.RdDailyStockSummaryDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface RdDailyStockSummaryMapper extends BaseMapperX<RdDailyStockSummaryDO> {

    default PageResult<RdDailyStockSummaryDO> selectPage(RdDailyStockSummaryPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<RdDailyStockSummaryDO>()
                .betweenIfPresent(RdDailyStockSummaryDO::getBizDate, reqVO.getBizDate())
                .likeIfPresent(RdDailyStockSummaryDO::getLocationCode, reqVO.getLocationCode())
                .likeIfPresent(RdDailyStockSummaryDO::getCustomerName, reqVO.getCustomerName())
                .likeIfPresent(RdDailyStockSummaryDO::getProductModel, reqVO.getProductModel())
                .likeIfPresent(RdDailyStockSummaryDO::getFurnaceNo, reqVO.getFurnaceNo())
                .likeIfPresent(RdDailyStockSummaryDO::getFurnaceLayer, reqVO.getFurnaceLayer())
                .orderByDesc(RdDailyStockSummaryDO::getBizDate)
                .orderByAsc(RdDailyStockSummaryDO::getLocationCode)
                .orderByAsc(RdDailyStockSummaryDO::getId));
    }

    @Insert("INSERT INTO rd_daily_stock_summary (biz_date, stock_lot_id, location_code, customer_name, "
            + "product_model, furnace_no, furnace_layer, production_qty, inbound_qty, outbound_qty, "
            + "transfer_in_qty, transfer_out_qty, return_qty, net_change_qty, warehouse_qty, stock_weight, "
            + "creator, create_time, updater, update_time, deleted) VALUES (#{bizDate}, #{stockLotId}, "
            + "#{locationCode}, #{customerName}, #{productModel}, #{furnaceNo}, #{furnaceLayer}, "
            + "#{productionQty}, #{inboundQty}, #{outboundQty}, #{transferInQty}, #{transferOutQty}, "
            + "#{returnQty}, #{netChangeQty}, #{warehouseQty}, #{stockWeight}, '', NOW(), '', NOW(), b'0') "
            + "ON DUPLICATE KEY UPDATE production_qty = production_qty + VALUES(production_qty), "
            + "inbound_qty = inbound_qty + VALUES(inbound_qty), outbound_qty = outbound_qty + VALUES(outbound_qty), "
            + "transfer_in_qty = transfer_in_qty + VALUES(transfer_in_qty), "
            + "transfer_out_qty = transfer_out_qty + VALUES(transfer_out_qty), "
            + "return_qty = return_qty + VALUES(return_qty), net_change_qty = net_change_qty + VALUES(net_change_qty), "
            + "warehouse_qty = VALUES(warehouse_qty), stock_weight = VALUES(stock_weight), "
            + "location_code = VALUES(location_code), customer_name = VALUES(customer_name), "
            + "product_model = VALUES(product_model), furnace_no = VALUES(furnace_no), "
            + "furnace_layer = VALUES(furnace_layer), update_time = NOW(), deleted = b'0'")
    int upsertIncrement(RdDailyStockSummaryDO summary);

    @Insert("INSERT INTO rd_daily_stock_summary (biz_date, stock_lot_id, location_code, customer_name, "
            + "product_model, furnace_no, furnace_layer, production_qty, inbound_qty, outbound_qty, "
            + "transfer_in_qty, transfer_out_qty, return_qty, net_change_qty, warehouse_qty, stock_weight, "
            + "creator, create_time, updater, update_time, deleted) VALUES (#{bizDate}, #{stockLotId}, "
            + "#{locationCode}, #{customerName}, #{productModel}, #{furnaceNo}, #{furnaceLayer}, "
            + "#{productionQty}, #{inboundQty}, #{outboundQty}, #{transferInQty}, #{transferOutQty}, "
            + "#{returnQty}, #{netChangeQty}, #{warehouseQty}, #{stockWeight}, '', NOW(), '', NOW(), b'0') "
            + "ON DUPLICATE KEY UPDATE production_qty = VALUES(production_qty), inbound_qty = VALUES(inbound_qty), "
            + "outbound_qty = VALUES(outbound_qty), transfer_in_qty = VALUES(transfer_in_qty), "
            + "transfer_out_qty = VALUES(transfer_out_qty), return_qty = VALUES(return_qty), "
            + "net_change_qty = VALUES(net_change_qty), warehouse_qty = VALUES(warehouse_qty), "
            + "stock_weight = VALUES(stock_weight), location_code = VALUES(location_code), "
            + "customer_name = VALUES(customer_name), product_model = VALUES(product_model), "
            + "furnace_no = VALUES(furnace_no), furnace_layer = VALUES(furnace_layer), "
            + "update_time = NOW(), deleted = b'0'")
    int upsertRebuilt(RdDailyStockSummaryDO summary);

    @Select("SELECT biz_date, stock_lot_id, location_code, customer_name, product_model, furnace_no, furnace_layer, "
            + "production_qty, inbound_qty, outbound_qty, transfer_in_qty, transfer_out_qty, return_qty, "
            + "net_change_qty, after_qty AS warehouse_qty, after_qty * package_weight AS stock_weight FROM ("
            + "SELECT t.*, ROW_NUMBER() OVER (PARTITION BY t.biz_date, t.stock_lot_id ORDER BY t.id DESC) AS row_num, "
            + "SUM(CASE WHEN t.transaction_type = 'PRODUCTION_IN' THEN t.quantity ELSE 0 END) "
            + "OVER (PARTITION BY t.biz_date, t.stock_lot_id) AS production_qty, "
            + "SUM(CASE WHEN t.transaction_type IN ('PRODUCTION_IN', 'INBOUND') THEN t.quantity ELSE 0 END) "
            + "OVER (PARTITION BY t.biz_date, t.stock_lot_id) AS inbound_qty, "
            + "SUM(CASE WHEN t.transaction_type = 'OUTBOUND' THEN -t.quantity ELSE 0 END) "
            + "OVER (PARTITION BY t.biz_date, t.stock_lot_id) AS outbound_qty, "
            + "SUM(CASE WHEN t.transaction_type = 'TRANSFER_IN' THEN t.quantity ELSE 0 END) "
            + "OVER (PARTITION BY t.biz_date, t.stock_lot_id) AS transfer_in_qty, "
            + "SUM(CASE WHEN t.transaction_type = 'TRANSFER_OUT' THEN -t.quantity ELSE 0 END) "
            + "OVER (PARTITION BY t.biz_date, t.stock_lot_id) AS transfer_out_qty, "
            + "SUM(CASE WHEN t.transaction_type = 'RETURN_IN' THEN t.quantity ELSE 0 END) "
            + "OVER (PARTITION BY t.biz_date, t.stock_lot_id) AS return_qty, "
            + "SUM(t.quantity) OVER (PARTITION BY t.biz_date, t.stock_lot_id) AS net_change_qty "
            + "FROM rd_stock_transaction t WHERE t.deleted = b'0' AND t.stock_lot_id IS NOT NULL "
            + "AND t.biz_date BETWEEN #{startDate} AND #{endDate}) daily WHERE row_num = 1 "
            + "ORDER BY biz_date, stock_lot_id")
    List<RdDailyStockSummaryDO> selectDailyAggregates(@Param("startDate") LocalDate startDate,
                                                       @Param("endDate") LocalDate endDate);

    @Select({
            "<script>",
            "SELECT biz_date, SUM(production_qty) AS production_qty, SUM(inbound_qty) AS inbound_qty,",
            "SUM(outbound_qty) AS outbound_qty, SUM(transfer_in_qty) AS transfer_in_qty,",
            "SUM(transfer_out_qty) AS transfer_out_qty, SUM(return_qty) AS return_qty,",
            "SUM(net_change_qty) AS net_change_qty FROM rd_daily_stock_summary",
            "FORCE INDEX (idx_rd_daily_summary_query) WHERE deleted = b'0'",
            "<if test='req.bizDate != null and req.bizDate.length == 2'>",
            "AND biz_date BETWEEN #{req.bizDate[0]} AND #{req.bizDate[1]}",
            "</if>",
            "<if test='req.locationCode != null and req.locationCode != \"\"'>",
            "AND location_code LIKE CONCAT('%', #{req.locationCode}, '%')",
            "</if>",
            "<if test='req.customerName != null and req.customerName != \"\"'>",
            "AND customer_name LIKE CONCAT('%', #{req.customerName}, '%')",
            "</if>",
            "<if test='req.productModel != null and req.productModel != \"\"'>",
            "AND product_model LIKE CONCAT('%', #{req.productModel}, '%')",
            "</if>",
            "<if test='req.furnaceNo != null and req.furnaceNo != \"\"'>",
            "AND furnace_no LIKE CONCAT('%', #{req.furnaceNo}, '%')",
            "</if>",
            "<if test='req.furnaceLayer != null and req.furnaceLayer != \"\"'>",
            "AND furnace_layer LIKE CONCAT('%', #{req.furnaceLayer}, '%')",
            "</if>",
            "GROUP BY biz_date ORDER BY biz_date",
            "</script>"
    })
    List<RdDailyStockTrendRespVO> selectTrend(@Param("req") RdDailyStockSummaryPageReqVO reqVO);

}
