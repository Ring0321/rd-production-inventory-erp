-- Rebuilds a dedicated local performance database. It never writes to rd_erp.
DROP DATABASE IF EXISTS `rd_erp_perf`;
CREATE DATABASE `rd_erp_perf` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE `rd_erp_perf`.`rd_stock_lot` LIKE `rd_erp`.`rd_stock_lot`;
CREATE TABLE `rd_erp_perf`.`rd_stock_transaction` LIKE `rd_erp`.`rd_stock_transaction`;
CREATE TABLE `rd_erp_perf`.`rd_sale_order_extend` LIKE `rd_erp`.`rd_sale_order_extend`;
CREATE TABLE `rd_erp_perf`.`rd_daily_stock_summary` LIKE `rd_erp`.`rd_daily_stock_summary`;
CREATE TABLE `rd_erp_perf`.`rd_excel_import_task` LIKE `rd_erp`.`rd_excel_import_task`;
CREATE TABLE `rd_erp_perf`.`rd_excel_import_row` LIKE `rd_erp`.`rd_excel_import_row`;

USE `rd_erp_perf`;
SET SESSION cte_max_recursion_depth = 25000;

CREATE TABLE `perf_sequence` (`n` int NOT NULL PRIMARY KEY) ENGINE=InnoDB;
INSERT INTO `perf_sequence` (`n`)
WITH RECURSIVE `sequence_rows` (`n`) AS (
  SELECT 1
  UNION ALL
  SELECT `n` + 1 FROM `sequence_rows` WHERE `n` < 20000
)
SELECT `n` FROM `sequence_rows`;

INSERT INTO `rd_stock_lot` (
  `tenant_id`, `location_id`, `location_code`, `customer_name`, `product_model`,
  `furnace_no`, `furnace_layer`, `opening_qty`, `inbound_qty`, `outbound_qty`,
  `warehouse_qty`, `locked_qty`, `package_weight`, `stock_weight`, `last_biz_date`,
  `status`, `remark`, `creator`, `updater`, `deleted`
)
SELECT 1,
       MOD(`n`, 200) + 1,
       CONCAT('PERF-LOC-', LPAD(MOD(`n`, 200), 3, '0')),
       CONCAT('PERF-CUSTOMER-', LPAD(MOD(`n`, 100), 3, '0')),
       CONCAT('PERF-MODEL-', LPAD(MOD(`n`, 50), 2, '0')),
       CONCAT('PERF-FURNACE-', LPAD(`n`, 5, '0')),
       CONCAT('LAYER-', MOD(`n`, 4)),
       100, 100, 0, 100, 0, 1000, 100000,
       DATE_ADD('2026-01-01', INTERVAL MOD(`n`, 180) DAY),
       'AVAILABLE', 'RD performance seed', 'perf', 'perf', b'0'
FROM `perf_sequence`;

INSERT INTO `rd_sale_order_extend` (
  `tenant_id`, `order_no`, `order_date`, `customer_name`, `product_model`,
  `required_qty`, `actual_output_qty`, `difference_qty`, `allocated_qty`,
  `transfer_in_qty`, `transfer_out_qty`, `adjusted_difference_qty`, `fulfilled_qty`,
  `status`, `remark`, `creator`, `updater`, `deleted`
)
SELECT 1,
       CONCAT('PERF-SO-', LPAD(`n`, 6, '0')),
       DATE_ADD('2026-01-01', INTERVAL MOD(`n`, 180) DAY),
       CONCAT('PERF-CUSTOMER-', LPAD(MOD(`n`, 100), 3, '0')),
       CONCAT('PERF-MODEL-', LPAD(MOD(`n`, 50), 2, '0')),
       100, MOD(`n`, 100), 100 - MOD(`n`, 100), 0, 0, 0,
       100 - MOD(`n`, 100), MOD(`n`, 100),
       CASE WHEN MOD(`n`, 4) = 0 THEN 'PARTIAL' ELSE 'OPEN' END,
       'RD performance seed', 'perf', 'perf', b'0'
FROM `perf_sequence`;

INSERT INTO `rd_stock_transaction` (
  `tenant_id`, `biz_date`, `transaction_type`, `stock_lot_id`, `location_id`,
  `location_code`, `customer_name`, `product_model`, `furnace_no`, `furnace_layer`,
  `quantity`, `package_weight`, `weight`, `before_qty`, `after_qty`,
  `source_type`, `source_no`, `source_line_no`, `operator_name`, `remark`,
  `creator`, `updater`, `deleted`
)
SELECT 1,
       DATE_ADD('2026-01-01', INTERVAL MOD(`n`, 180) DAY),
       CASE WHEN MOD(`n`, 3) = 0 THEN 'OUTBOUND' ELSE 'INBOUND' END,
       `n`, MOD(`n`, 200) + 1,
       CONCAT('PERF-LOC-', LPAD(MOD(`n`, 200), 3, '0')),
       CONCAT('PERF-CUSTOMER-', LPAD(MOD(`n`, 100), 3, '0')),
       CONCAT('PERF-MODEL-', LPAD(MOD(`n`, 50), 2, '0')),
       CONCAT('PERF-FURNACE-', LPAD(`n`, 5, '0')),
       CONCAT('LAYER-', MOD(`n`, 4)),
       1, 1000, 1000, 99, 100,
       'PERF_SEED', CONCAT('PERF-TXN-', LPAD(`n`, 6, '0')),
       CONCAT('LAYER-', MOD(`n`, 4)), 'perf', 'RD performance seed',
       'perf', 'perf', b'0'
FROM `perf_sequence`;

INSERT INTO `rd_daily_stock_summary` (
  `tenant_id`, `biz_date`, `stock_lot_id`, `location_code`, `customer_name`,
  `product_model`, `furnace_no`, `furnace_layer`, `production_qty`, `inbound_qty`,
  `outbound_qty`, `transfer_in_qty`, `transfer_out_qty`, `return_qty`,
  `net_change_qty`, `warehouse_qty`, `stock_weight`, `creator`, `updater`, `deleted`
)
SELECT 1,
       DATE_ADD('2026-01-01', INTERVAL MOD(`n`, 180) DAY),
       `n`,
       CONCAT('PERF-LOC-', LPAD(MOD(`n`, 200), 3, '0')),
       CONCAT('PERF-CUSTOMER-', LPAD(MOD(`n`, 100), 3, '0')),
       CONCAT('PERF-MODEL-', LPAD(MOD(`n`, 50), 2, '0')),
       CONCAT('PERF-FURNACE-', LPAD(`n`, 5, '0')),
       CONCAT('LAYER-', MOD(`n`, 4)),
       1, 1, MOD(`n`, 2), 0, 0, 0, 1 - MOD(`n`, 2), 100, 100000,
       'perf', 'perf', b'0'
FROM `perf_sequence`;

INSERT INTO `rd_excel_import_task` (
  `id`, `tenant_id`, `file_name`, `sheet_name`, `biz_date`, `status`, `total_rows`,
  `valid_rows`, `failed_rows`, `skipped_rows`, `imported_rows`, `creator`, `updater`, `deleted`
) VALUES (
  1, 1, 'PERF-20000.xlsx', '货位统计明细表', '2026-07-10', 'PRECHECKED', 20000,
  20000, 0, 0, 0, 'perf', 'perf', b'0'
);

INSERT INTO `rd_excel_import_row` (
  `tenant_id`, `task_id`, `row_no`, `location_code`, `customer_name`, `product_model`,
  `furnace_no`, `furnace_layer`, `source_opening_qty`, `warehouse_qty`,
  `package_weight`, `stock_weight`, `status`, `creator`, `updater`, `deleted`
)
SELECT 1, 1, `n` + 2,
       CONCAT('PERF-LOC-', LPAD(MOD(`n`, 200), 3, '0')),
       CONCAT('PERF-CUSTOMER-', LPAD(MOD(`n`, 100), 3, '0')),
       CONCAT('PERF-MODEL-', LPAD(MOD(`n`, 50), 2, '0')),
       CONCAT('PERF-FURNACE-', LPAD(`n`, 5, '0')),
       CONCAT('LAYER-', MOD(`n`, 4)),
       0, 100, 1000, 100000, 'VALID', 'perf', 'perf', b'0'
FROM `perf_sequence`;

ANALYZE TABLE `rd_stock_lot`, `rd_stock_transaction`, `rd_sale_order_extend`,
              `rd_daily_stock_summary`, `rd_excel_import_row`;

SELECT 'rd_stock_lot' AS `table_name`, COUNT(*) AS `row_count` FROM `rd_stock_lot`
UNION ALL SELECT 'rd_stock_transaction', COUNT(*) FROM `rd_stock_transaction`
UNION ALL SELECT 'rd_sale_order_extend', COUNT(*) FROM `rd_sale_order_extend`
UNION ALL SELECT 'rd_daily_stock_summary', COUNT(*) FROM `rd_daily_stock_summary`
UNION ALL SELECT 'rd_excel_import_row', COUNT(*) FROM `rd_excel_import_row`;
