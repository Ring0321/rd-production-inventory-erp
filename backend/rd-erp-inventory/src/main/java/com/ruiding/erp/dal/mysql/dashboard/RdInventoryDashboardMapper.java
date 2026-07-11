package com.ruiding.erp.dal.mysql.dashboard;

import com.ruiding.erp.controller.admin.dashboard.vo.RdInventoryDashboardRespVO.MovementMetric;
import com.ruiding.erp.controller.admin.dashboard.vo.RdInventoryDashboardRespVO.OpenOrderItem;
import com.ruiding.erp.controller.admin.dashboard.vo.RdInventoryDashboardRespVO.OrderMetric;
import com.ruiding.erp.controller.admin.dashboard.vo.RdInventoryDashboardRespVO.OrderStatusItem;
import com.ruiding.erp.controller.admin.dashboard.vo.RdInventoryDashboardRespVO.RankingItem;
import com.ruiding.erp.controller.admin.dashboard.vo.RdInventoryDashboardRespVO.StockMetric;
import com.ruiding.erp.controller.admin.dashboard.vo.RdInventoryDashboardRespVO.TrendItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface RdInventoryDashboardMapper {

    @Select("SELECT COUNT(*) AS stock_lot_count, COALESCE(SUM(warehouse_qty), 0) AS warehouse_qty, "
            + "COALESCE(SUM(locked_qty), 0) AS locked_qty, "
            + "COALESCE(SUM(GREATEST(warehouse_qty - COALESCE(locked_qty, 0), 0)), 0) AS available_qty, "
            + "COALESCE(SUM(stock_weight), 0) AS stock_weight "
            + "FROM rd_stock_lot FORCE INDEX (idx_rd_stock_lot_status_qty) "
            + "WHERE deleted = b'0' AND status IN ('AVAILABLE', 'LOCKED') "
            + "AND warehouse_qty > 0")
    StockMetric selectStockMetric();

    @Select("SELECT COUNT(*) AS open_order_count, COALESCE(SUM(required_qty), 0) AS required_qty, "
            + "COALESCE(SUM(GREATEST(adjusted_difference_qty, 0)), 0) AS remaining_qty, "
            + "COALESCE(SUM(fulfilled_qty), 0) AS fulfilled_qty FROM rd_sale_order_extend "
            + "WHERE deleted = b'0' AND status IN ('OPEN', 'PARTIAL')")
    OrderMetric selectOpenOrderMetric();

    @Select("SELECT COALESCE(SUM(production_qty), 0) AS production_qty, "
            + "COALESCE(SUM(inbound_qty), 0) AS inbound_qty, COALESCE(SUM(outbound_qty), 0) AS outbound_qty, "
            + "COALESCE(SUM(transfer_in_qty), 0) AS transfer_in_qty, "
            + "COALESCE(SUM(transfer_out_qty), 0) AS transfer_out_qty, "
            + "COALESCE(SUM(return_qty), 0) AS return_qty, "
            + "COALESCE(SUM(net_change_qty), 0) AS net_change_qty FROM rd_daily_stock_summary "
            + "FORCE INDEX (idx_rd_daily_summary_query) "
            + "WHERE deleted = b'0' AND biz_date = #{bizDate}")
    MovementMetric selectMovementMetric(@Param("bizDate") LocalDate bizDate);

    @Select("SELECT biz_date, SUM(production_qty) AS production_qty, SUM(inbound_qty) AS inbound_qty, "
            + "SUM(outbound_qty) AS outbound_qty, SUM(transfer_in_qty) AS transfer_in_qty, "
            + "SUM(transfer_out_qty) AS transfer_out_qty, SUM(return_qty) AS return_qty, "
            + "SUM(net_change_qty) AS net_change_qty FROM rd_daily_stock_summary "
            + "FORCE INDEX (idx_rd_daily_summary_query) "
            + "WHERE deleted = b'0' AND biz_date BETWEEN #{startDate} AND #{endDate} "
            + "GROUP BY biz_date ORDER BY biz_date")
    List<TrendItem> selectTrend(@Param("startDate") LocalDate startDate,
                                @Param("endDate") LocalDate endDate);

    @Select("SELECT customer_name AS name, COUNT(*) AS stock_lot_count, "
            + "SUM(warehouse_qty) AS warehouse_qty, SUM(stock_weight) AS stock_weight "
            + "FROM rd_stock_lot WHERE deleted = b'0' AND status IN ('AVAILABLE', 'LOCKED') "
            + "AND warehouse_qty > 0 "
            + "GROUP BY customer_name ORDER BY stock_weight DESC, warehouse_qty DESC LIMIT 10")
    List<RankingItem> selectCustomerRanking();

    @Select("SELECT product_model AS name, COUNT(*) AS stock_lot_count, "
            + "SUM(warehouse_qty) AS warehouse_qty, SUM(stock_weight) AS stock_weight "
            + "FROM rd_stock_lot WHERE deleted = b'0' AND status IN ('AVAILABLE', 'LOCKED') "
            + "AND warehouse_qty > 0 "
            + "GROUP BY product_model ORDER BY stock_weight DESC, warehouse_qty DESC LIMIT 10")
    List<RankingItem> selectModelRanking();

    @Select("SELECT status, COUNT(*) AS order_count, SUM(required_qty) AS required_qty, "
            + "SUM(fulfilled_qty) AS fulfilled_qty FROM rd_sale_order_extend WHERE deleted = b'0' "
            + "GROUP BY status ORDER BY FIELD(status, 'OPEN', 'PARTIAL', 'FULFILLED', 'CLOSED', 'CANCELLED')")
    List<OrderStatusItem> selectOrderStatuses();

    @Select("SELECT id, order_no, order_date, customer_name, product_model, required_qty, actual_output_qty, "
            + "adjusted_difference_qty, fulfilled_qty, status FROM rd_sale_order_extend "
            + "WHERE deleted = b'0' AND status IN ('OPEN', 'PARTIAL') "
            + "ORDER BY order_date, id LIMIT 8")
    List<OpenOrderItem> selectOpenOrders();

}
