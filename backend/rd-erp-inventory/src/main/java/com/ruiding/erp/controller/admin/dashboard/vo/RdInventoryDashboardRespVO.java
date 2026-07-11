package com.ruiding.erp.controller.admin.dashboard.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "管理后台 - 瑞鼎库存看板 Response VO")
@Data
public class RdInventoryDashboardRespVO {

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate asOfDate;
    private StockMetric stock;
    private OrderMetric order;
    private MovementMetric todayMovement;
    private List<TrendItem> trend;
    private List<RankingItem> customerRanking;
    private List<RankingItem> modelRanking;
    private List<OrderStatusItem> orderStatuses;
    private List<OpenOrderItem> openOrders;

    @Data
    public static class StockMetric {
        private Long stockLotCount;
        private BigDecimal warehouseQty;
        private BigDecimal lockedQty;
        private BigDecimal availableQty;
        private BigDecimal stockWeight;
    }

    @Data
    public static class OrderMetric {
        private Long openOrderCount;
        private BigDecimal requiredQty;
        private BigDecimal remainingQty;
        private BigDecimal fulfilledQty;
    }

    @Data
    public static class MovementMetric {
        private BigDecimal productionQty;
        private BigDecimal inboundQty;
        private BigDecimal outboundQty;
        private BigDecimal transferInQty;
        private BigDecimal transferOutQty;
        private BigDecimal returnQty;
        private BigDecimal netChangeQty;
    }

    @Data
    public static class TrendItem extends MovementMetric {
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private LocalDate bizDate;
    }

    @Data
    public static class RankingItem {
        private String name;
        private Long stockLotCount;
        private BigDecimal warehouseQty;
        private BigDecimal stockWeight;
    }

    @Data
    public static class OrderStatusItem {
        private String status;
        private Long orderCount;
        private BigDecimal requiredQty;
        private BigDecimal fulfilledQty;
    }

    @Data
    public static class OpenOrderItem {
        private Long id;
        private String orderNo;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private LocalDate orderDate;
        private String customerName;
        private String productModel;
        private BigDecimal requiredQty;
        private BigDecimal actualOutputQty;
        private BigDecimal adjustedDifferenceQty;
        private BigDecimal fulfilledQty;
        private String status;
    }

}
