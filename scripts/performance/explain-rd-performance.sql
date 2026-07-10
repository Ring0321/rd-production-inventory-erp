USE `rd_erp_perf`;

SELECT 'stock-list' AS `case_name`;
EXPLAIN ANALYZE
SELECT `id`, `location_code`, `customer_name`, `product_model`, `warehouse_qty`, `last_biz_date`
FROM `rd_stock_lot`
WHERE `tenant_id` = 1 AND `deleted` = b'0' AND `status` = 'AVAILABLE'
ORDER BY `last_biz_date` DESC, `id` DESC
LIMIT 20;

SELECT 'stock-match-same-customer' AS `case_name`;
EXPLAIN ANALYZE
SELECT `id`, `warehouse_qty`, `locked_qty`, `last_biz_date`
FROM `rd_stock_lot`
WHERE `tenant_id` = 1 AND `deleted` = b'0'
  AND `customer_name` = 'PERF-CUSTOMER-001'
  AND `product_model` = 'PERF-MODEL-01'
  AND `status` IN ('AVAILABLE', 'LOCKED')
  AND `warehouse_qty` > `locked_qty`
ORDER BY `last_biz_date`, `id`
LIMIT 20;

SELECT 'order-open-list' AS `case_name`;
EXPLAIN ANALYZE
SELECT `id`, `order_no`, `order_date`, `customer_name`, `product_model`, `required_qty`, `status`
FROM `rd_sale_order_extend`
WHERE `tenant_id` = 1 AND `deleted` = b'0' AND `status` IN ('OPEN', 'PARTIAL')
ORDER BY `order_date`, `id`
LIMIT 20;

SELECT 'daily-trend' AS `case_name`;
EXPLAIN ANALYZE
SELECT `biz_date`, SUM(`inbound_qty`) AS `inbound_qty`, SUM(`outbound_qty`) AS `outbound_qty`,
       SUM(`net_change_qty`) AS `net_change_qty`
FROM `rd_daily_stock_summary` FORCE INDEX (`idx_rd_daily_summary_query`)
WHERE `tenant_id` = 1 AND `deleted` = b'0'
  AND `biz_date` BETWEEN '2026-05-01' AND '2026-06-30'
GROUP BY `biz_date`
ORDER BY `biz_date`;

SELECT 'dashboard-stock-metric' AS `case_name`;
EXPLAIN ANALYZE
SELECT COUNT(*) AS `stock_lot_count`, SUM(`warehouse_qty`) AS `warehouse_qty`,
       SUM(`locked_qty`) AS `locked_qty`, SUM(`stock_weight`) AS `stock_weight`
FROM `rd_stock_lot` FORCE INDEX (`idx_rd_stock_lot_status_qty`)
WHERE `tenant_id` = 1 AND `deleted` = b'0'
  AND `status` IN ('AVAILABLE', 'LOCKED') AND `warehouse_qty` > 0;

SELECT 'stock-transaction-list' AS `case_name`;
EXPLAIN ANALYZE
SELECT `id`, `biz_date`, `transaction_type`, `stock_lot_id`, `quantity`, `source_no`
FROM `rd_stock_transaction`
WHERE `tenant_id` = 1 AND `deleted` = b'0'
  AND `biz_date` BETWEEN '2026-05-01' AND '2026-06-30'
  AND `transaction_type` = 'OUTBOUND'
ORDER BY `biz_date` DESC, `id` DESC
LIMIT 20;

SELECT 'excel-valid-rows' AS `case_name`;
EXPLAIN ANALYZE
SELECT `id`, `row_no`, `location_code`, `customer_name`, `product_model`, `warehouse_qty`
FROM `rd_excel_import_row`
WHERE `tenant_id` = 1 AND `deleted` = b'0' AND `task_id` = 1 AND `status` = 'VALID'
ORDER BY `row_no`;
