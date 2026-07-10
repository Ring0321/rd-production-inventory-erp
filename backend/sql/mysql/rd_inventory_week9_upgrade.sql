-- Week 9 upgrade for databases created with an earlier rd_inventory.sql.
-- Do not run this file after the current full baseline; use one path or the other.

SET NAMES utf8mb4;

ALTER TABLE `rd_excel_import_task`
  ADD COLUMN `biz_date` date DEFAULT NULL COMMENT 'Current stock baseline date' AFTER `sheet_name`,
  MODIFY COLUMN `status` varchar(32) NOT NULL DEFAULT 'PRECHECKED' COMMENT 'PRECHECKED/CONFIRMING/SUCCESS/FAILED',
  CHANGE COLUMN `success_rows` `valid_rows` int NOT NULL DEFAULT 0 COMMENT 'Valid positive-stock rows',
  ADD COLUMN `skipped_rows` int NOT NULL DEFAULT 0 COMMENT 'Skipped zero-stock rows' AFTER `failed_rows`,
  ADD COLUMN `imported_rows` int NOT NULL DEFAULT 0 COMMENT 'Confirmed imported rows' AFTER `skipped_rows`,
  ADD COLUMN `confirm_time` datetime DEFAULT NULL COMMENT 'Confirmation time' AFTER `imported_rows`;

UPDATE `rd_excel_import_task`
SET `biz_date` = DATE(`create_time`)
WHERE `biz_date` IS NULL;

ALTER TABLE `rd_excel_import_task`
  MODIFY COLUMN `biz_date` date NOT NULL COMMENT 'Current stock baseline date';

CREATE TABLE IF NOT EXISTS `rd_excel_import_row` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Import staging row ID',
  `task_id` bigint NOT NULL COMMENT 'Import task ID',
  `row_no` int NOT NULL COMMENT 'Excel row number',
  `location_code` varchar(64) DEFAULT NULL COMMENT 'Location code',
  `customer_name` varchar(128) DEFAULT NULL COMMENT 'Customer',
  `product_model` varchar(128) DEFAULT NULL COMMENT 'Product model',
  `furnace_no` varchar(64) DEFAULT NULL COMMENT 'Production furnace number',
  `furnace_layer` varchar(32) DEFAULT NULL COMMENT 'Furnace layer',
  `source_opening_qty` decimal(24,6) DEFAULT NULL COMMENT 'Original Excel opening quantity',
  `warehouse_qty` decimal(24,6) DEFAULT NULL COMMENT 'Current warehouse quantity to import',
  `package_weight` decimal(24,6) DEFAULT NULL COMMENT 'Single package weight',
  `stock_weight` decimal(24,6) DEFAULT NULL COMMENT 'Current stock weight',
  `status` varchar(32) NOT NULL COMMENT 'VALID/ERROR/SKIPPED/IMPORTED',
  `error_message` varchar(512) DEFAULT NULL COMMENT 'Combined row validation message',
  `stock_lot_id` bigint DEFAULT NULL COMMENT 'Created stock lot ID',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_import_row` (`task_id`, `row_no`, `deleted`),
  KEY `idx_rd_import_row_task_status` (`task_id`, `status`, `row_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD Excel import staging row';

INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(600220, '库存 Excel 导入', 'rd:excel-import:query', 2, 40, 600200, 'import', 'ep:upload-filled', 'rd/stock/import/index', 'RdStockImport', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600221, '库存 Excel 预检', 'rd:excel-import:create', 3, 10, 600220, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600222, '确认库存 Excel 导入', 'rd:excel-import:confirm', 3, 20, 600220, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600223, '导出库存 Excel 错误', 'rd:excel-import:export', 3, 30, 600220, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `permission` = VALUES(`permission`),
  `parent_id` = VALUES(`parent_id`),
  `component` = VALUES(`component`),
  `component_name` = VALUES(`component_name`),
  `deleted` = b'0',
  `update_time` = NOW();
