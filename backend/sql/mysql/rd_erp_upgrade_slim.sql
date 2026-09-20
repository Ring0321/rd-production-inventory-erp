-- Ruiding ERP in-place slimming migration.
-- Run only against an isolated restore after backup verification.
-- This script preserves users, user-role links, roles, and all rd_* business rows.

SET NAMES utf8mb4;
SET SQL_SAFE_UPDATES = 0;
START TRANSACTION;

CREATE TABLE IF NOT EXISTS `rd_customer_product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Customer product relation ID',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT 'Tenant ID',
  `customer_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Customer',
  `product_model` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Product model',
  `source_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'BUSINESS' COMMENT 'EXCEL/BUSINESS',
  `last_used_time` datetime DEFAULT NULL COMMENT 'Last successful business use time',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0 enabled, 1 disabled',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_customer_product` (`tenant_id`,`customer_name`,`product_model`,`deleted`),
  KEY `idx_rd_customer_product_customer` (`tenant_id`,`status`,`deleted`,`customer_name`),
  KEY `idx_rd_customer_product_model` (`tenant_id`,`status`,`deleted`,`product_model`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD customer and product model options';

INSERT INTO `rd_customer_product`
(`tenant_id`, `customer_name`, `product_model`, `source_type`, `last_used_time`, `status`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(1, '江苏贝特瑞', '026', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '江苏贝特瑞', '035', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '江苏贝特瑞', '035JC', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '江苏贝特瑞', '038/CC-H', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '江苏贝特瑞', '038', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '江苏贝特瑞', '041', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '江苏贝特瑞', '052', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '江苏贝特瑞', '080', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '江苏贝特瑞', '092', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '贝特瑞产品一部', '015', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '贝特瑞产品一部', '052', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '贝特瑞产品一部', '13031', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '贝特瑞产品二部', '027', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '贝特瑞产品二部', '035', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '贝特瑞产品二部', '035JC', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '贝特瑞产品二部', '092', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '贝特瑞产品二部', '001', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '贝特瑞产品二部', '001D1L', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '贝特瑞产品三部', '035', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '贝特瑞产品三部', '065', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '瑞安贝特瑞', '031', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '恒鼎新材', '027', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '恒鼎新材', '035', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '恒鼎新材', 'A1-GR-ZC-001J', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '恒鼎新材', 'A1-GR-ZC-001-K', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '湖州启源金灿', 'YG15', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '恒鼎新材/江苏贝特瑞', '035', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '中科星城', '5PA', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '中科星城', '1CC-2M', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '中科星城', '1CY-2M', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '中科星城', '1CY-2H', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '中科星城', '1CF-2H', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '中科星城', '1SO-3G-M', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '中科星城', '1SI-3G-M', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '中科星城', '1SA-2G-M', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '中科星城', '1SL-3G-M', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '中科星城', '1C083-2M', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '中科星城', '1C090-2H', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '中科星城', '1SC-5G-M', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '中科星城', '1S139-3G-M', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '湖北皇恩烨', 'S11石墨化-混011&02', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '重庆渝盛星', 'CC-H', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '重庆渝盛星', 'NGC15', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '重庆渝盛星', 'C1', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '云南坤天', '石墨化A料XJ-11', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '四川坤天', '石墨化A料P6-9', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '湖南镕锂', 'G-PC071', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '瑞鼎新材料', '026', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '瑞鼎新材料', '035', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '瑞鼎新材料', '035JC', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '瑞鼎新材料', '038', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '瑞鼎新材料', '038/CC-H', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '瑞鼎新材料', '041', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '瑞鼎新材料', '049', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '瑞鼎新材料', '052', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '瑞鼎新材料', '062', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '瑞鼎新材料', '065', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '瑞鼎新材料', '077', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '瑞鼎新材料', '081', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '瑞鼎新材料', '13031', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '瑞鼎新材料', '磁性物', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '瑞鼎新材料', '混合料', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '瑞鼎新材料', '破碎料', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '贝特瑞产品三部退货', '035', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '江苏贝特瑞退货', '035', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '江苏贝特瑞退货', '041', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0')
ON DUPLICATE KEY UPDATE
  `source_type` = VALUES(`source_type`), `status` = 0, `deleted` = b'0',
  `updater` = 'rd-customer-product-migration', `update_time` = NOW();

SET @matrix_index_exists = (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'rd_stock_transaction'
    AND index_name = 'idx_rd_stock_txn_matrix'
);
SET @matrix_index_sql = IF(
  @matrix_index_exists = 0,
  'ALTER TABLE rd_stock_transaction ADD INDEX idx_rd_stock_txn_matrix (tenant_id, customer_name, product_model, biz_date, stock_lot_id, deleted)',
  'SELECT 1'
);
PREPARE matrix_index_stmt FROM @matrix_index_sql;
EXECUTE matrix_index_stmt;
DEALLOCATE PREPARE matrix_index_stmt;

DROP PROCEDURE IF EXISTS `rd_add_column_if_missing`;
DELIMITER $$
CREATE PROCEDURE `rd_add_column_if_missing`(IN p_table varchar(64), IN p_column varchar(64), IN p_definition text)
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = p_table AND column_name = p_column) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
    PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL rd_add_column_if_missing('rd_location','warehouse_code',"varchar(64) NOT NULL DEFAULT 'FINISHED' AFTER location_name");
CALL rd_add_column_if_missing('rd_location','area_code',"varchar(64) DEFAULT NULL AFTER warehouse_name");
CALL rd_add_column_if_missing('rd_location','row_code',"varchar(32) DEFAULT NULL AFTER area_name");
CALL rd_add_column_if_missing('rd_location','column_code',"varchar(32) DEFAULT NULL AFTER row_code");
CALL rd_add_column_if_missing('rd_location','level_code',"varchar(32) DEFAULT NULL AFTER column_code");
CALL rd_add_column_if_missing('rd_location','capacity_packages',"decimal(24,6) DEFAULT NULL AFTER area_name");
CALL rd_add_column_if_missing('rd_location','capacity_weight',"decimal(24,6) DEFAULT NULL AFTER capacity_packages");
CALL rd_add_column_if_missing('rd_location','occupied_packages',"decimal(24,6) NOT NULL DEFAULT 0.000000 AFTER capacity_weight");
CALL rd_add_column_if_missing('rd_location','occupied_weight',"decimal(24,6) NOT NULL DEFAULT 0.000000 AFTER occupied_packages");
CALL rd_add_column_if_missing('rd_location','reserved_packages',"decimal(24,6) NOT NULL DEFAULT 0.000000 AFTER occupied_weight");
CALL rd_add_column_if_missing('rd_location','reserved_weight',"decimal(24,6) NOT NULL DEFAULT 0.000000 AFTER reserved_packages");
CALL rd_add_column_if_missing('rd_location','allowed_category',"varchar(128) DEFAULT NULL AFTER capacity_packages");
CALL rd_add_column_if_missing('rd_location','frozen',"bit(1) NOT NULL DEFAULT b'0' AFTER allowed_category");
CALL rd_add_column_if_missing('rd_location','stocktake_order_id',"bigint DEFAULT NULL AFTER frozen");
CALL rd_add_column_if_missing('rd_location','enabled',"bit(1) NOT NULL DEFAULT b'1' AFTER stocktake_order_id");
CALL rd_add_column_if_missing('rd_location','location_status',"varchar(32) NOT NULL DEFAULT 'AVAILABLE' AFTER enabled");
CALL rd_add_column_if_missing('rd_location','version',"int NOT NULL DEFAULT 0 AFTER location_status");
CALL rd_add_column_if_missing('rd_stock_transaction','package_id',"bigint DEFAULT NULL AFTER stock_lot_id");
CALL rd_add_column_if_missing('rd_stock_transaction','idempotency_key',"varchar(128) DEFAULT NULL AFTER operator_name");
CALL rd_add_column_if_missing('rd_stock_transaction','reversal_transaction_id',"bigint DEFAULT NULL AFTER idempotency_key");
CALL rd_add_column_if_missing('rd_stock_transaction','reviewer_id',"bigint DEFAULT NULL AFTER reversal_transaction_id");
CALL rd_add_column_if_missing('rd_stock_transaction','reviewer_name',"varchar(64) DEFAULT NULL AFTER reviewer_id");

CREATE TABLE IF NOT EXISTS `rd_product_master` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1', `product_code` varchar(64) NOT NULL,
  `product_name` varchar(128) NOT NULL, `customer_name` varchar(128) NOT NULL, `product_model` varchar(128) NOT NULL,
  `category` varchar(64) DEFAULT NULL, `unit` varchar(32) NOT NULL DEFAULT '包', `default_package_weight` decimal(24,6) NOT NULL,
  `quality_standard` text, `enabled` bit(1) NOT NULL DEFAULT b'1', `remark` varchar(512) DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_rd_product_master_code` (`tenant_id`,`product_code`,`deleted`),
  KEY `idx_rd_product_master_option` (`tenant_id`,`enabled`,`deleted`,`customer_name`,`product_model`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `rd_package_code_sequence` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1', `biz_date` date NOT NULL,
  `current_value` int NOT NULL DEFAULT 0, `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_rd_package_sequence_date` (`tenant_id`,`biz_date`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `rd_package_unit` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1', `package_code` varchar(32) NOT NULL,
  `qr_code` varchar(128) NOT NULL, `production_output_id` bigint NOT NULL, `production_date` date NOT NULL,
  `furnace_out_date` date DEFAULT NULL, `shift_code` varchar(32) DEFAULT NULL, `customer_name` varchar(128) NOT NULL,
  `product_name` varchar(128) DEFAULT NULL, `product_model` varchar(128) NOT NULL, `furnace_no` varchar(64) NOT NULL,
  `furnace_layer` varchar(32) NOT NULL, `package_weight` decimal(24,6) NOT NULL, `location_id` bigint DEFAULT NULL,
  `location_code` varchar(64) DEFAULT NULL, `stock_lot_id` bigint DEFAULT NULL, `status` varchar(32) NOT NULL,
  `version` int NOT NULL DEFAULT 0, `remark` varchar(512) DEFAULT NULL, `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_rd_package_code` (`tenant_id`,`package_code`,`deleted`),
  UNIQUE KEY `uk_rd_package_qr` (`tenant_id`,`qr_code`,`deleted`),
  KEY `idx_rd_package_production` (`tenant_id`,`production_output_id`,`deleted`,`status`),
  KEY `idx_rd_package_stock` (`tenant_id`,`stock_lot_id`,`location_code`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `rd_quality_inspection` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1', `inspection_no` varchar(64) NOT NULL,
  `package_id` bigint NOT NULL, `status` varchar(32) NOT NULL, `conclusion` varchar(32) DEFAULT NULL,
  `reject_reason` varchar(512) DEFAULT NULL, `submitter_id` bigint DEFAULT NULL, `submitter_name` varchar(64) DEFAULT NULL,
  `submit_time` datetime DEFAULT NULL, `reviewer_id` bigint DEFAULT NULL, `reviewer_name` varchar(64) DEFAULT NULL,
  `review_time` datetime DEFAULT NULL, `version` int NOT NULL DEFAULT 0, `remark` varchar(512) DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_rd_quality_no` (`tenant_id`,`inspection_no`,`deleted`),
  KEY `idx_rd_quality_package` (`tenant_id`,`package_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `rd_quality_inspection_item` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1', `inspection_id` bigint NOT NULL,
  `item_code` varchar(64) NOT NULL, `item_name` varchar(128) NOT NULL, `standard_min` decimal(24,6) DEFAULT NULL,
  `standard_max` decimal(24,6) DEFAULT NULL, `measured_value` decimal(24,6) DEFAULT NULL, `unit` varchar(32) DEFAULT NULL,
  `qualified` bit(1) DEFAULT NULL, `remark` varchar(512) DEFAULT NULL, `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`), KEY `idx_rd_quality_item` (`tenant_id`,`inspection_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `rd_inbound_order` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1', `order_no` varchar(64) NOT NULL,
  `biz_date` date NOT NULL, `source_type` varchar(32) DEFAULT NULL, `production_output_id` bigint DEFAULT NULL,
  `total_packages` decimal(24,6) NOT NULL DEFAULT 0, `total_weight` decimal(24,6) NOT NULL DEFAULT 0,
  `status` varchar(32) NOT NULL, `submitter_id` bigint DEFAULT NULL, `submitter_name` varchar(64) DEFAULT NULL,
  `submit_time` datetime DEFAULT NULL, `reviewer_id` bigint DEFAULT NULL, `reviewer_name` varchar(64) DEFAULT NULL,
  `review_time` datetime DEFAULT NULL, `poster_id` bigint DEFAULT NULL, `poster_name` varchar(64) DEFAULT NULL,
  `post_time` datetime DEFAULT NULL, `reverser_id` bigint DEFAULT NULL, `reverser_name` varchar(64) DEFAULT NULL,
  `reverse_time` datetime DEFAULT NULL, `reverse_reason` varchar(512) DEFAULT NULL, `version` int NOT NULL DEFAULT 0,
  `remark` varchar(512) DEFAULT NULL, `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`), UNIQUE KEY `uk_rd_inbound_no` (`tenant_id`,`order_no`,`deleted`),
  KEY `idx_rd_inbound_status_date` (`tenant_id`,`status`,`biz_date`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `rd_inbound_order_line` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1', `order_id` bigint NOT NULL,
  `package_id` bigint NOT NULL, `location_id` bigint DEFAULT NULL, `location_code` varchar(64) NOT NULL,
  `quantity` decimal(24,6) NOT NULL DEFAULT 1, `weight` decimal(24,6) NOT NULL, `stock_lot_id` bigint DEFAULT NULL,
  `stock_transaction_id` bigint DEFAULT NULL, `remark` varchar(512) DEFAULT NULL, `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_rd_inbound_package` (`tenant_id`,`package_id`,`deleted`),
  KEY `idx_rd_inbound_line_order` (`tenant_id`,`order_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `rd_label_print_log` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1', `package_id` bigint NOT NULL,
  `template_code` varchar(64) NOT NULL, `print_sequence` int NOT NULL, `reprint` bit(1) NOT NULL DEFAULT b'0',
  `reprint_reason` varchar(512) DEFAULT NULL, `printer_id` bigint DEFAULT NULL, `printer_name` varchar(64) DEFAULT NULL,
  `print_time` datetime NOT NULL, `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`), KEY `idx_rd_label_package` (`tenant_id`,`package_id`,`deleted`,`print_sequence`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `rd_scan_event` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1', `biz_type` varchar(32) NOT NULL,
  `biz_id` bigint DEFAULT NULL, `package_id` bigint DEFAULT NULL, `package_code` varchar(32) NOT NULL,
  `device_code` varchar(64) DEFAULT NULL, `operator_id` bigint DEFAULT NULL, `operator_name` varchar(64) DEFAULT NULL,
  `success` bit(1) NOT NULL, `error_message` varchar(512) DEFAULT NULL, `scan_time` datetime NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`), KEY `idx_rd_scan_package_time` (`tenant_id`,`package_code`,`scan_time`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `rd_putaway_task` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1',
  `task_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL, `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING_PUTAWAY',
  `planned_location_id` bigint DEFAULT NULL, `planned_location_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `final_location_id` bigint DEFAULT NULL, `final_location_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `package_count` int NOT NULL DEFAULT '0', `scanned_package_count` int NOT NULL DEFAULT '0',
  `exception_package_count` int NOT NULL DEFAULT '0', `total_weight` decimal(24,6) NOT NULL DEFAULT '0.000000',
  `warehouse_keeper_id` bigint DEFAULT NULL, `warehouse_keeper_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `forklift_driver_id` bigint DEFAULT NULL, `forklift_driver_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `assigned_time` datetime DEFAULT NULL, `start_time` datetime DEFAULT NULL, `scan_complete_time` datetime DEFAULT NULL,
  `confirm_time` datetime DEFAULT NULL, `cancel_time` datetime DEFAULT NULL,
  `cancel_reason` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL, `version` int NOT NULL DEFAULT '0',
  `remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL, `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_putaway_task_no` (`tenant_id`,`task_no`,`deleted`),
  KEY `idx_rd_putaway_task_status` (`tenant_id`,`status`,`deleted`,`create_time`,`id`),
  KEY `idx_rd_putaway_task_driver` (`tenant_id`,`forklift_driver_id`,`status`,`deleted`,`id`),
  KEY `idx_rd_putaway_task_keeper` (`tenant_id`,`warehouse_keeper_id`,`status`,`deleted`,`id`),
  KEY `idx_rd_putaway_task_plan_location` (`tenant_id`,`planned_location_id`,`status`,`deleted`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD putaway task';

CREATE TABLE IF NOT EXISTS `rd_putaway_task_line` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1', `task_id` bigint NOT NULL,
  `line_no` int NOT NULL, `package_id` bigint NOT NULL, `package_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `line_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `customer_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL, `product_model` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `furnace_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL, `furnace_layer` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `package_weight` decimal(24,6) NOT NULL, `scan_count` int NOT NULL DEFAULT '0',
  `first_scan_time` datetime DEFAULT NULL, `last_scan_time` datetime DEFAULT NULL,
  `inbound_order_id` bigint DEFAULT NULL, `inbound_order_line_id` bigint DEFAULT NULL,
  `stock_lot_id` bigint DEFAULT NULL, `stock_transaction_id` bigint DEFAULT NULL, `version` int NOT NULL DEFAULT '0',
  `remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL, `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_putaway_line_no` (`tenant_id`,`task_id`,`line_no`,`deleted`),
  UNIQUE KEY `uk_rd_putaway_line_package` (`tenant_id`,`task_id`,`package_id`,`deleted`),
  KEY `idx_rd_putaway_line_task` (`tenant_id`,`task_id`,`line_status`,`deleted`,`line_no`),
  KEY `idx_rd_putaway_line_package` (`tenant_id`,`package_id`,`line_status`,`deleted`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD putaway task package line';

CREATE TABLE IF NOT EXISTS `rd_putaway_scan_record` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1', `task_id` bigint NOT NULL,
  `task_line_id` bigint DEFAULT NULL, `package_id` bigint DEFAULT NULL,
  `package_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `idempotency_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `scan_result` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL, `success` bit(1) NOT NULL,
  `error_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `error_message` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `task_status_before` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `task_status_after` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `device_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL, `operator_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `device_time` datetime DEFAULT NULL, `scan_time` datetime NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_putaway_scan_idempotency` (`tenant_id`,`task_id`,`idempotency_key`,`deleted`),
  KEY `idx_rd_putaway_scan_task` (`tenant_id`,`task_id`,`scan_time`,`deleted`,`id`),
  KEY `idx_rd_putaway_scan_package` (`tenant_id`,`package_code`,`scan_time`,`deleted`,`id`),
  KEY `idx_rd_putaway_scan_operator` (`tenant_id`,`operator_id`,`scan_time`,`deleted`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD putaway scan record';

CREATE TABLE IF NOT EXISTS `rd_putaway_exception` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1',
  `exception_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL, `task_id` bigint NOT NULL,
  `task_line_id` bigint DEFAULT NULL, `package_id` bigint DEFAULT NULL,
  `package_code` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `exception_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `exception_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OPEN',
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `attachment_refs` text COLLATE utf8mb4_unicode_ci,
  `reporter_id` bigint DEFAULT NULL, `reporter_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reporter_role` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL, `reported_time` datetime NOT NULL,
  `handler_id` bigint DEFAULT NULL, `handler_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `handle_time` datetime DEFAULT NULL, `handling_result` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `resolution_action` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `previous_location_id` bigint DEFAULT NULL, `previous_location_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `resolved_location_id` bigint DEFAULT NULL, `resolved_location_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0', `remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_putaway_exception_no` (`tenant_id`,`exception_no`,`deleted`),
  KEY `idx_rd_putaway_exception_task` (`tenant_id`,`task_id`,`exception_status`,`deleted`,`reported_time`,`id`),
  KEY `idx_rd_putaway_exception_handler` (`tenant_id`,`handler_id`,`exception_status`,`deleted`,`id`),
  KEY `idx_rd_putaway_exception_package` (`tenant_id`,`package_id`,`deleted`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD putaway exception';

CREATE TABLE IF NOT EXISTS `rd_stocktake_order` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1',
  `stocktake_no` varchar(64) NOT NULL, `stocktake_date` date NOT NULL, `location_id` bigint NOT NULL,
  `location_code` varchar(64) NOT NULL, `status` varchar(32) NOT NULL, `version` int NOT NULL DEFAULT 0,
  `snapshot_time` datetime DEFAULT NULL, `book_package_qty` decimal(24,6) NOT NULL DEFAULT 0,
  `counted_package_qty` decimal(24,6) NOT NULL DEFAULT 0, `shortage_qty` decimal(24,6) NOT NULL DEFAULT 0,
  `surplus_qty` decimal(24,6) NOT NULL DEFAULT 0, `book_weight` decimal(24,6) NOT NULL DEFAULT 0,
  `counted_weight` decimal(24,6) NOT NULL DEFAULT 0, `business_creator_id` bigint DEFAULT NULL,
  `business_creator_name` varchar(64) DEFAULT NULL, `submitter_id` bigint DEFAULT NULL,
  `submitter_name` varchar(64) DEFAULT NULL, `submit_time` datetime DEFAULT NULL, `reviewer_id` bigint DEFAULT NULL,
  `reviewer_name` varchar(64) DEFAULT NULL, `review_time` datetime DEFAULT NULL, `adjuster_id` bigint DEFAULT NULL,
  `adjuster_name` varchar(64) DEFAULT NULL, `adjust_time` datetime DEFAULT NULL, `closer_id` bigint DEFAULT NULL,
  `closer_name` varchar(64) DEFAULT NULL, `close_time` datetime DEFAULT NULL, `canceller_id` bigint DEFAULT NULL,
  `canceller_name` varchar(64) DEFAULT NULL, `cancel_time` datetime DEFAULT NULL, `remark` varchar(512) DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_stocktake_no` (`tenant_id`,`stocktake_no`,`deleted`),
  KEY `idx_rd_stocktake_status_date` (`tenant_id`,`status`,`stocktake_date`,`deleted`),
  KEY `idx_rd_stocktake_location` (`tenant_id`,`location_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `rd_stocktake_order_line` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1', `order_id` bigint NOT NULL,
  `package_id` bigint NOT NULL, `package_code` varchar(32) NOT NULL, `stock_lot_id` bigint NOT NULL,
  `customer_name` varchar(128) NOT NULL,
  `product_model` varchar(128) NOT NULL, `furnace_no` varchar(64) NOT NULL, `furnace_layer` varchar(32) NOT NULL,
  `package_weight` decimal(24,6) NOT NULL, `book_qty` decimal(24,6) NOT NULL DEFAULT 0,
  `counted_qty` decimal(24,6) NOT NULL DEFAULT 0, `difference_qty` decimal(24,6) NOT NULL DEFAULT 0,
  `result` varchar(32) NOT NULL DEFAULT 'PENDING', `scanner_id` bigint DEFAULT NULL,
  `scanner_name` varchar(64) DEFAULT NULL, `scan_time` datetime DEFAULT NULL, `stock_transaction_id` bigint DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL, `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_stocktake_package` (`tenant_id`,`order_id`,`package_id`,`deleted`),
  KEY `idx_rd_stocktake_line_order` (`tenant_id`,`order_id`,`result`,`deleted`,`id`),
  KEY `idx_rd_stocktake_line_package` (`tenant_id`,`package_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CALL rd_add_column_if_missing('rd_stocktake_order','business_creator_id',"bigint DEFAULT NULL AFTER counted_weight");
CALL rd_add_column_if_missing('rd_stocktake_order','business_creator_name',"varchar(64) DEFAULT NULL AFTER business_creator_id");
DROP PROCEDURE `rd_add_column_if_missing`;

DROP PROCEDURE IF EXISTS `rd_add_index_if_missing`;
DELIMITER $$
CREATE PROCEDURE `rd_add_index_if_missing`(IN p_table varchar(64), IN p_index varchar(64), IN p_definition text)
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE()
      AND table_name = p_table AND index_name = p_index) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD ', p_definition);
    PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;
CALL rd_add_index_if_missing('rd_location','idx_rd_location_map',
  'INDEX `idx_rd_location_map` (`tenant_id`,`warehouse_code`,`area_code`,`row_code`,`column_code`,`level_code`,`deleted`)');
CALL rd_add_index_if_missing('rd_location','idx_rd_location_availability',
  'INDEX `idx_rd_location_availability` (`tenant_id`,`location_status`,`enabled`,`frozen`,`deleted`)');
DROP PROCEDURE `rd_add_index_if_missing`;

CREATE TABLE IF NOT EXISTS `rd_transfer_order_package` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1',
  `transfer_order_id` bigint NOT NULL, `package_id` bigint NOT NULL, `package_code` varchar(64) NOT NULL,
  `from_stock_lot_id` bigint NOT NULL, `to_stock_lot_id` bigint NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_transfer_package` (`tenant_id`,`transfer_order_id`,`package_id`,`deleted`),
  KEY `idx_rd_transfer_package_code` (`tenant_id`,`package_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD transfer package trace';

SET @outbound_workflow_columns = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'rd_outbound_order' AND column_name = 'submitter_id');
SET @outbound_workflow_sql = IF(@outbound_workflow_columns = 0,
  'ALTER TABLE rd_outbound_order ADD COLUMN version int NOT NULL DEFAULT 0 AFTER status, ADD COLUMN submitter_id bigint DEFAULT NULL AFTER version, ADD COLUMN submitter_name varchar(64) DEFAULT NULL AFTER submitter_id, ADD COLUMN submit_time datetime DEFAULT NULL AFTER submitter_name, ADD COLUMN reviewer_id bigint DEFAULT NULL AFTER submit_time, ADD COLUMN reviewer_name varchar(64) DEFAULT NULL AFTER reviewer_id, ADD COLUMN review_time datetime DEFAULT NULL AFTER reviewer_name, ADD COLUMN poster_id bigint DEFAULT NULL AFTER review_time, ADD COLUMN poster_name varchar(64) DEFAULT NULL AFTER poster_id, ADD COLUMN post_time datetime DEFAULT NULL AFTER poster_name, ADD COLUMN reverser_id bigint DEFAULT NULL AFTER post_time, ADD COLUMN reverser_name varchar(64) DEFAULT NULL AFTER reverser_id, ADD COLUMN reverse_time datetime DEFAULT NULL AFTER reverser_name, ADD COLUMN reverse_reason varchar(512) DEFAULT NULL AFTER reverse_time',
  'SELECT 1');
PREPARE outbound_workflow_stmt FROM @outbound_workflow_sql; EXECUTE outbound_workflow_stmt; DEALLOCATE PREPARE outbound_workflow_stmt;

CREATE TABLE IF NOT EXISTS `rd_outbound_order_line` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1', `order_id` bigint NOT NULL,
  `package_id` bigint NOT NULL, `package_code` varchar(32) NOT NULL, `stock_lot_id` bigint NOT NULL,
  `stock_transaction_id` bigint DEFAULT NULL, `location_id` bigint DEFAULT NULL, `location_code` varchar(64) DEFAULT NULL,
  `customer_name` varchar(128) NOT NULL, `product_model` varchar(128) NOT NULL, `furnace_no` varchar(64) NOT NULL,
  `furnace_layer` varchar(32) NOT NULL, `package_weight` decimal(24,6) NOT NULL, `scan_device` varchar(64) DEFAULT NULL,
  `scanner_id` bigint DEFAULT NULL, `scanner_name` varchar(64) DEFAULT NULL, `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_rd_outbound_line_package` (`tenant_id`,`order_id`,`package_id`,`deleted`),
  KEY `idx_rd_outbound_line_order` (`tenant_id`,`order_id`,`deleted`,`id`),
  KEY `idx_rd_outbound_line_package` (`tenant_id`,`package_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @stock_idem_index = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
  AND table_name = 'rd_stock_transaction' AND index_name = 'uk_rd_stock_txn_idempotency');
SET @stock_idem_sql = IF(@stock_idem_index = 0,
  'ALTER TABLE rd_stock_transaction ADD UNIQUE KEY uk_rd_stock_txn_idempotency (tenant_id,idempotency_key,deleted)', 'SELECT 1');
PREPARE stock_idem_stmt FROM @stock_idem_sql; EXECUTE stock_idem_stmt; DEALLOCATE PREPARE stock_idem_stmt;

SET @stock_package_index = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
  AND table_name = 'rd_stock_transaction' AND index_name = 'idx_rd_stock_txn_package');
SET @stock_package_sql = IF(@stock_package_index = 0,
  'ALTER TABLE rd_stock_transaction ADD INDEX idx_rd_stock_txn_package (tenant_id,package_id,biz_date,deleted)', 'SELECT 1');
PREPARE stock_package_stmt FROM @stock_package_sql; EXECUTE stock_package_stmt; DEALLOCATE PREPARE stock_package_stmt;

CREATE TEMPORARY TABLE IF NOT EXISTS `rd_keep_menu_id` (
  `id` bigint NOT NULL PRIMARY KEY
) ENGINE=MEMORY;
TRUNCATE TABLE `rd_keep_menu_id`;

INSERT IGNORE INTO `rd_keep_menu_id` (`id`) VALUES
(1),(100),(101),(103),(104),(105),(108),(500),(501),
(1001),(1002),(1003),(1004),(1005),(1006),(1007),
(1008),(1009),(1010),(1011),(1012),(1063),(1064),(1065),
(1017),(1018),(1019),(1020),(1021),(1022),(1023),(1024),(1025),
(1026),(1027),(1028),(1029),(1030),(1040),(1042),(1043),(1045);

INSERT IGNORE INTO `rd_keep_menu_id` (`id`)
SELECT `id` FROM `system_menu` WHERE `id` BETWEEN 600000 AND 600999;

UPDATE `system_menu`
SET `deleted` = b'1', `updater` = 'rd-slim-migration', `update_time` = NOW()
WHERE `id` NOT IN (SELECT `id` FROM `rd_keep_menu_id`);

DELETE `rm` FROM `system_role_menu` `rm`
LEFT JOIN `system_menu` `m` ON `m`.`id` = `rm`.`menu_id`
WHERE `m`.`id` IS NULL OR `m`.`deleted` = b'1';

-- Authentication channels are intentionally reset. Reconfigure them after credential rotation.
DELETE FROM `system_sms_log`;
DELETE FROM `system_sms_code`;
DELETE FROM `system_sms_template`;
DELETE FROM `system_sms_channel`;
DELETE FROM `system_social_user_bind`;
DELETE FROM `system_social_user`;
DELETE FROM `system_social_client`;
DELETE FROM `system_oauth2_access_token`;
DELETE FROM `system_oauth2_refresh_token`;

-- External storage configurations may contain credentials. Keep database storage tables empty.
DELETE FROM `infra_file`;
DELETE FROM `infra_file_content`;
DELETE FROM `infra_file_config`;

-- Retain only the RD daily summary job and its execution history.
DELETE FROM `infra_job` WHERE `handler_name` <> 'rdDailyStockSummaryJob';
DELETE `jl` FROM `infra_job_log` `jl`
LEFT JOIN `infra_job` `j` ON `j`.`id` = `jl`.`job_id`
WHERE `j`.`id` IS NULL;

-- Remove platform demo and discontinued feature tables after the backup has been verified.
DROP TABLE IF EXISTS `yudao_demo01_contact`;
DROP TABLE IF EXISTS `yudao_demo02_category`;
DROP TABLE IF EXISTS `yudao_demo03_course`;
DROP TABLE IF EXISTS `yudao_demo03_grade`;
DROP TABLE IF EXISTS `yudao_demo03_student`;
DROP TABLE IF EXISTS `infra_codegen_column`;
DROP TABLE IF EXISTS `infra_codegen_table`;
DROP TABLE IF EXISTS `infra_data_source_config`;
DROP TABLE IF EXISTS `system_notice`;
DROP TABLE IF EXISTS `system_notify_message`;
DROP TABLE IF EXISTS `system_notify_template`;
DROP TABLE IF EXISTS `system_mail_log`;
DROP TABLE IF EXISTS `system_mail_template`;
DROP TABLE IF EXISTS `system_mail_account`;
DROP TABLE IF EXISTS `system_oauth2_approve`;
DROP TABLE IF EXISTS `system_oauth2_code`;
DROP TABLE IF EXISTS `system_oauth2_client`;

UPDATE `system_tenant`
SET `name` = '瑞鼎生产库存订单ERP', `status` = 0,
    `expire_time` = '2099-12-31 23:59:59',
    `updater` = 'rd-slim-migration', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = 1;

UPDATE `system_tenant`
SET `status` = 1, `updater` = 'rd-slim-migration', `update_time` = NOW()
WHERE `id` <> 1;

INSERT INTO `system_post`
(`id`, `code`, `name`, `sort`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`) VALUES
(600001, 'RD_PRODUCTION', '生产录入岗', 10, 0, '生产产量录入与确认', 'rd-slim-migration', NOW(), 'rd-slim-migration', NOW(), b'0', 1),
(600002, 'RD_WAREHOUSE', '库房管理岗', 20, 0, '入库、调拨、出库与库存调整', 'rd-slim-migration', NOW(), 'rd-slim-migration', NOW(), b'0', 1),
(600003, 'RD_SALES', '销售订单岗', 30, 0, '销售订单与订单匹配', 'rd-slim-migration', NOW(), 'rd-slim-migration', NOW(), b'0', 1),
(600004, 'RD_AUDIT', '审核报表岗', 40, 0, '审核、日志与报表', 'rd-slim-migration', NOW(), 'rd-slim-migration', NOW(), b'0', 1),
(600005, 'RD_READONLY', '只读查询岗', 50, 0, '业务只读查询', 'rd-slim-migration', NOW(), 'rd-slim-migration', NOW(), b'0', 1)
ON DUPLICATE KEY UPDATE `code`=VALUES(`code`), `name`=VALUES(`name`), `sort`=VALUES(`sort`), `status`=VALUES(`status`), `remark`=VALUES(`remark`), `tenant_id`=1, `deleted`=b'0';

INSERT INTO `system_role`
(`id`, `name`, `code`, `sort`, `data_scope`, `data_scope_dept_ids`, `status`, `type`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`) VALUES
(600001, '生产录入', 'rd_production', 10, 1, '', 0, 2, '生产产量录入与查询', 'rd-slim-migration', NOW(), 'rd-slim-migration', NOW(), b'0', 1),
(600002, '库房管理', 'rd_warehouse', 20, 1, '', 0, 2, '库存、调拨、出库管理', 'rd-slim-migration', NOW(), 'rd-slim-migration', NOW(), b'0', 1),
(600003, '销售订单', 'rd_sales', 30, 1, '', 0, 2, '销售订单与订单匹配', 'rd-slim-migration', NOW(), 'rd-slim-migration', NOW(), b'0', 1),
(600004, '审核报表', 'rd_audit', 40, 1, '', 0, 2, '审核、报表与日志查询', 'rd-slim-migration', NOW(), 'rd-slim-migration', NOW(), b'0', 1),
(600005, '只读查询', 'rd_readonly', 50, 1, '', 0, 2, '业务数据只读', 'rd-slim-migration', NOW(), 'rd-slim-migration', NOW(), b'0', 1)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `code`=VALUES(`code`), `sort`=VALUES(`sort`), `data_scope`=VALUES(`data_scope`), `status`=VALUES(`status`), `type`=VALUES(`type`), `remark`=VALUES(`remark`), `tenant_id`=1, `deleted`=b'0';

INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(600213, '库存动态明细', 'rd:stock-matrix:query', 2, 25, 600200, 'matrix', 'ep:grid', 'rd/stock/matrix/index', 'RdStockMatrix', 0, b'1', b'1', b'1', 'rd-slim-migration', NOW(), 'rd-slim-migration', NOW(), b'0'),
(600214, '库存盘点', 'rd:stocktake:query', 2, 45, 600200, 'stocktake', 'ep:document-checked', 'rd/stock/stocktake/index', 'RdStocktake', 0, b'1', b'1', b'1', 'rd-slim-migration', NOW(), 'rd-slim-migration', NOW(), b'0'),
(600215, '入库上架', 'rd:putaway-task:query', 2, 38, 600200, 'putaway', 'ep:position', 'rd/stock/putaway/index', 'RdPutawayWorkbench', 0, b'1', b'1', b'1', 'rd-slim-migration', NOW(), 'rd-slim-migration', NOW(), b'0'),
(600224, '客户型号选项查询', 'rd:customer-product:query', 3, 90, 600000, '', '', '', NULL, 0, b'0', b'0', b'0', 'rd-slim-migration', NOW(), 'rd-slim-migration', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `permission`=VALUES(`permission`), `type`=VALUES(`type`),
  `sort`=VALUES(`sort`), `parent_id`=VALUES(`parent_id`), `path`=VALUES(`path`), `icon`=VALUES(`icon`),
  `component`=VALUES(`component`), `component_name`=VALUES(`component_name`), `status`=0,
  `visible`=VALUES(`visible`), `keep_alive`=VALUES(`keep_alive`), `always_show`=VALUES(`always_show`),
  `updater`='rd-slim-migration', `update_time`=NOW(), `deleted`=b'0';

INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(600230,'产品资料查询','rd:product-master:query',3,230,600000,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0'),
(600231,'产品资料新增','rd:product-master:create',3,231,600000,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0'),
(600232,'产品资料修改','rd:product-master:update',3,232,600000,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0'),
(600233,'仓库货位查询','rd:warehouse-location:query',3,233,600000,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0'),
(600234,'仓库货位新增','rd:warehouse-location:create',3,234,600000,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0'),
(600235,'仓库货位修改','rd:warehouse-location:update',3,235,600000,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0'),
(600236,'包码查询','rd:package-unit:query',3,236,600000,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0'),
(600237,'包码生成','rd:package-unit:generate',3,237,600000,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0'),
(600238,'标签打印','rd:label-print:print',3,238,600000,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0'),
(600239,'标签补打','rd:label-print:reprint',3,239,600000,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0'),
(600240,'质检查询','rd:quality-inspection:query',3,240,600000,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0'),
(600241,'质检创建','rd:quality-inspection:create',3,241,600000,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0'),
(600242,'质检提交','rd:quality-inspection:submit',3,242,600000,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0'),
(600243,'质检审核','rd:quality-inspection:approve',3,243,600000,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0'),
(600244,'入库单查询','rd:inbound-order:query',3,244,600000,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0'),
(600245,'入库单创建','rd:inbound-order:create',3,245,600000,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0'),
(600246,'入库单提交','rd:inbound-order:submit',3,246,600000,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0'),
(600247,'入库单复核','rd:inbound-order:verify',3,247,600000,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0'),
(600248,'入库单过账','rd:inbound-order:post',3,248,600000,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0'),
(600249,'入库单撤销','rd:inbound-order:reverse',3,249,600000,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0')
,(600250,'出库扫码','rd:outbound-scan:scan',3,250,600207,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0')
,(600251,'出库提交','rd:outbound-scan:submit',3,251,600207,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0')
,(600252,'出库审核','rd:outbound-scan:approve',3,252,600207,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0')
,(600253,'出库过账','rd:outbound-scan:post',3,253,600207,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0')
,(600254,'出库反向撤销','rd:outbound-scan:reverse',3,254,600207,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0')
,(600260,'盘点单新增','rd:stocktake:create',3,260,600214,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0')
,(600261,'盘点冻结','rd:stocktake:freeze',3,261,600214,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0')
,(600262,'盘点扫码','rd:stocktake:scan',3,262,600214,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0')
,(600263,'盘点提交','rd:stocktake:submit',3,263,600214,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0')
,(600264,'盘点审核','rd:stocktake:approve',3,264,600214,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0')
,(600265,'盘点差异调整','rd:stocktake:adjust',3,265,600214,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0')
,(600266,'盘点关闭','rd:stocktake:close',3,266,600214,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0')
,(600267,'盘点取消','rd:stocktake:cancel',3,267,600214,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0')
,(600270,'创建上架任务','rd:putaway-task:create',3,270,600215,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0')
,(600271,'调整上架派单','rd:putaway-task:assign',3,271,600215,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0')
,(600272,'执行上架任务','rd:putaway-task:execute',3,272,600215,'','','',NULL,0,b'0',b'0',b'0','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`deleted`=b'0',`update_time`=NOW();

DELETE FROM `system_role_menu`
WHERE `tenant_id` = 1 AND `role_id` IN (600001,600002,600003,600004,600005,600006,600007,600008);

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT 600001, id, 'rd-slim-migration', NOW(), 'rd-slim-migration', NOW(), b'0', 1 FROM `system_menu` WHERE id IN (600000,600010,600100,600101,600102,600103,600104,600105,600224,600500,600501)
UNION ALL SELECT 600002, id, 'rd-slim-migration', NOW(), 'rd-slim-migration', NOW(), b'0', 1 FROM `system_menu` WHERE id IN (600000,600010,600200,600201,600202,600203,600204,600205,600206,600207,600208,600209,600210,600211,600212,600213,600214,600220,600221,600222,600223,600224,600260,600261,600262,600263,600264,600265,600266,600267,600400,600401,600402,600403,600404,600405,600406,600500,600501,600502,600503)
UNION ALL SELECT 600003, id, 'rd-slim-migration', NOW(), 'rd-slim-migration', NOW(), b'0', 1 FROM `system_menu` WHERE id IN (600000,600010,600224,600300,600301,600302,600303,600304,600305,600306,600310,600311,600312,600313,600400,600401,600402,600403,600404,600405,600406,600500,600501)
UNION ALL SELECT 600004, id, 'rd-slim-migration', NOW(), 'rd-slim-migration', NOW(), b'0', 1 FROM `system_menu` WHERE id IN (1,108,500,501,1040,1042,1043,1045,600000,600010,600100,600101,600200,600201,600203,600207,600213,600214,600224,600300,600301,600310,600400,600401,600500,600501,600502,600503)
UNION ALL SELECT 600005, id, 'rd-slim-migration', NOW(), 'rd-slim-migration', NOW(), b'0', 1 FROM `system_menu` WHERE id IN (600000,600010,600100,600101,600200,600201,600203,600207,600213,600214,600220,600224,600300,600301,600310,600400,600401,600500,600501);

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 600001,id,'rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0',1 FROM system_menu WHERE id IN (600230,600233,600236,600237,600238,600239,600240,600241,600242,600244,600245,600246)
UNION ALL SELECT 600002,id,'rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0',1 FROM system_menu WHERE id BETWEEN 600230 AND 600249
UNION ALL SELECT 600004,id,'rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0',1 FROM system_menu WHERE id IN (600230,600233,600236,600240,600243,600244,600247,600248,600249)
UNION ALL SELECT 600005,id,'rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0',1 FROM system_menu WHERE id IN (600230,600233,600236,600240,600244);

INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(600225,'基础资料','',1,60,600000,'masterdata','ep:files',NULL,NULL,0,b'1',b'1',b'1','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0'),
(600226,'质量管理','',1,70,600000,'quality','ep:checked',NULL,NULL,0,b'1',b'1',b'1','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`type`=VALUES(`type`),`sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`path`=VALUES(`path`),`icon`=VALUES(`icon`),`status`=0,`visible`=b'1',`deleted`=b'0',`update_time`=NOW();

UPDATE `system_menu` SET `name`='产品资料',`type`=2,`sort`=10,`parent_id`=600225,`path`='product',`icon`='ep:goods',`component`='rd/masterdata/product/index',`component_name`='RdProductMaster',`visible`=b'1',`keep_alive`=b'1' WHERE `id`=600230;
UPDATE `system_menu` SET `name`='仓库货位',`type`=2,`sort`=20,`parent_id`=600225,`path`='location',`icon`='ep:location',`component`='rd/masterdata/location/index',`component_name`='RdWarehouseLocation',`visible`=b'1',`keep_alive`=b'1' WHERE `id`=600233;
UPDATE `system_menu` SET `name`='包码库存',`type`=2,`sort`=35,`parent_id`=600200,`path`='package',`icon`='ep:postcard',`component`='rd/stock/package/index',`component_name`='RdPackageUnit',`visible`=b'1',`keep_alive`=b'1' WHERE `id`=600236;
UPDATE `system_menu` SET `name`='质量检验',`type`=2,`sort`=10,`parent_id`=600226,`path`='inspection',`icon`='ep:circle-check',`component`='rd/quality/inspection/index',`component_name`='RdQualityInspection',`visible`=b'1',`keep_alive`=b'1' WHERE `id`=600240;
UPDATE `system_menu` SET `name`='入库单',`type`=2,`sort`=40,`parent_id`=600200,`path`='inbound',`icon`='ep:download',`component`='rd/stock/inbound/index',`component_name`='RdInboundOrder',`visible`=b'1',`keep_alive`=b'1' WHERE `id`=600244;

INSERT IGNORE INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT role_id, menu_id, 'rd-slim-migration', NOW(), 'rd-slim-migration', NOW(), b'0', 1
FROM (SELECT 600001 role_id,600225 menu_id UNION ALL SELECT 600001,600226 UNION ALL SELECT 600002,600225 UNION ALL SELECT 600002,600226 UNION ALL SELECT 600004,600225 UNION ALL SELECT 600004,600226 UNION ALL SELECT 600005,600225 UNION ALL SELECT 600005,600226) x;

INSERT INTO `system_post` (`id`,`code`,`name`,`sort`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`) VALUES
(600006,'RD_QUALITY','质量检验岗',25,0,'质量数据录入、提交、审核与追溯','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0',1)
ON DUPLICATE KEY UPDATE `code`=VALUES(`code`),`name`=VALUES(`name`),`status`=0,`deleted`=b'0',`tenant_id`=1;

INSERT INTO `system_role` (`id`,`name`,`code`,`sort`,`data_scope`,`data_scope_dept_ids`,`status`,`type`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`) VALUES
(600006,'质量管理','rd_quality',25,1,'',0,2,'质量检验录入、审核与追溯','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0',1)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`code`=VALUES(`code`),`status`=0,`type`=2,`deleted`=b'0',`tenant_id`=1;

INSERT IGNORE INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 600006,id,'rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0',1 FROM system_menu
WHERE id IN (600000,600225,600226,600230,600233,600236,600240,600241,600242,600243);

INSERT INTO `system_post` (`id`,`code`,`name`,`sort`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`) VALUES
(600007,'RD_WAREHOUSE_OPERATOR','库房操作岗',22,0,'包码扫码、出入库制单与盘点录入','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0',1)
ON DUPLICATE KEY UPDATE `code`=VALUES(`code`),`name`=VALUES(`name`),`status`=0,`deleted`=b'0',`tenant_id`=1;

INSERT INTO `system_role` (`id`,`name`,`code`,`sort`,`data_scope`,`data_scope_dept_ids`,`status`,`type`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`) VALUES
(600007,'库房操作','rd_warehouse_operator',22,1,'',0,2,'扫码入库、扫码出库和盘点录入','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0',1)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`code`=VALUES(`code`),`status`=0,`type`=2,`deleted`=b'0',`tenant_id`=1;

INSERT IGNORE INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 600007,id,'rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0',1 FROM system_menu
WHERE id IN (600000,600200,600201,600203,600207,600208,600209,600214,600236,600244,600250,600251,600260,600261,600262,600263,600267);

INSERT INTO `system_post` (`id`,`code`,`name`,`sort`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`) VALUES
(600008,'RD_FORKLIFT_DRIVER','叉车司机岗',24,0,'执行上架与装车任务、扫码和异常上报','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0',1)
ON DUPLICATE KEY UPDATE `code`=VALUES(`code`),`name`=VALUES(`name`),`status`=0,`deleted`=b'0',`tenant_id`=1;

INSERT INTO `system_role` (`id`,`name`,`code`,`sort`,`data_scope`,`data_scope_dept_ids`,`status`,`type`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`) VALUES
(600008,'叉车司机','rd_forklift_driver',24,5,'',0,2,'仅执行分配给本人的上架与装车任务','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0',1)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`code`=VALUES(`code`),`status`=0,`type`=2,`data_scope`=5,`deleted`=b'0',`tenant_id`=1;

INSERT IGNORE INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 600002,id,'rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0',1 FROM system_menu WHERE id IN (600215,600270,600271)
UNION ALL SELECT 600008,id,'rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0',1 FROM system_menu WHERE id IN (600272);

INSERT IGNORE INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 600002,id,'rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0',1 FROM system_menu WHERE id IN (600250,600251,600252,600253,600254)
UNION ALL SELECT 600004,id,'rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0',1 FROM system_menu WHERE id IN (600252,600254);

-- Order split and production confirmation upgrade (idempotent).
DROP PROCEDURE IF EXISTS `rd_order_split_add_column`;
DELIMITER $$
CREATE PROCEDURE `rd_order_split_add_column`(IN p_table varchar(64), IN p_column varchar(64), IN p_definition text)
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = p_table AND column_name = p_column) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
    PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;
CALL rd_order_split_add_column('rd_production_output','version',"int NOT NULL DEFAULT 1 AFTER remark");
CALL rd_order_split_add_column('rd_sale_order_extend','manual_output_qty',"decimal(24,6) NOT NULL DEFAULT 0.000000 AFTER required_qty");
CALL rd_order_split_add_column('rd_sale_order_extend','split_output_qty',"decimal(24,6) NOT NULL DEFAULT 0.000000 AFTER manual_output_qty");
UPDATE `rd_sale_order_extend` SET `manual_output_qty`=`actual_output_qty`, `split_output_qty`=0
WHERE `manual_output_qty`=0 AND `split_output_qty`=0 AND `actual_output_qty`<>0;
DROP PROCEDURE `rd_order_split_add_column`;

CREATE TABLE IF NOT EXISTS `rd_order_split_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT 1, `rule_code` varchar(64) NOT NULL,
  `rule_name` varchar(128) NOT NULL, `customer_name` varchar(128) NOT NULL, `product_model` varchar(128) NOT NULL,
  `default_furnace_load_qty` decimal(24,6) NOT NULL, `yield_rate_percent` decimal(12,6) NOT NULL,
  `enabled` bit(1) NOT NULL DEFAULT b'1', `version` int NOT NULL DEFAULT 1, `remark` varchar(512) DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_rd_split_rule_code` (`tenant_id`,`rule_code`,`deleted`),
  KEY `idx_rd_split_rule_customer` (`tenant_id`,`customer_name`,`product_model`,`enabled`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `rd_order_split_rule_line` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT 1, `rule_id` bigint NOT NULL,
  `sequence_no` int NOT NULL, `furnace_layer` varchar(32) NOT NULL, `allocation_type` varchar(16) NOT NULL,
  `allocation_value` decimal(24,6) NOT NULL, `remark` varchar(512) DEFAULT NULL, `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_rd_split_rule_line_seq` (`tenant_id`,`rule_id`,`sequence_no`,`deleted`),
  KEY `idx_rd_split_rule_line_rule` (`tenant_id`,`rule_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `rd_order_split_result` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT 1, `split_no` varchar(64) NOT NULL,
  `split_date` date NOT NULL, `sale_order_id` bigint NOT NULL, `sale_order_no` varchar(64) NOT NULL, `rule_id` bigint NOT NULL,
  `rule_name_snapshot` varchar(128) NOT NULL, `customer_name_snapshot` varchar(128) NOT NULL,
  `product_model_snapshot` varchar(128) NOT NULL, `furnace_load_qty` decimal(24,6) NOT NULL,
  `yield_rate_percent` decimal(12,6) NOT NULL, `expected_output_qty` decimal(24,6) NOT NULL,
  `final_planned_output_qty` decimal(24,6) NOT NULL, `variance_qty` decimal(24,6) NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT', `version` int NOT NULL DEFAULT 1,
  `confirmer_id` bigint DEFAULT NULL, `confirmer_name` varchar(64) DEFAULT NULL, `confirm_time` datetime DEFAULT NULL,
  `canceller_id` bigint DEFAULT NULL, `canceller_name` varchar(64) DEFAULT NULL, `cancel_time` datetime DEFAULT NULL,
  `cancel_reason` varchar(512) DEFAULT NULL, `remark` varchar(512) DEFAULT NULL, `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_rd_split_result_no` (`tenant_id`,`split_no`,`deleted`),
  KEY `idx_rd_split_result_order` (`tenant_id`,`sale_order_id`,`status`,`deleted`,`split_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `rd_order_split_result_line` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT 1, `result_id` bigint NOT NULL,
  `sequence_no` int NOT NULL, `furnace_layer` varchar(32) NOT NULL, `allocation_type_snapshot` varchar(16) NOT NULL,
  `allocation_value_snapshot` decimal(24,6) NOT NULL, `calculated_base_qty` decimal(24,6) NOT NULL,
  `manual_adjustment_qty` decimal(24,6) NOT NULL DEFAULT 0, `final_planned_qty` decimal(24,6) NOT NULL,
  `actual_bound_qty` decimal(24,6) NOT NULL DEFAULT 0, `adjustment_reason` varchar(512) DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_rd_split_result_line_seq` (`tenant_id`,`result_id`,`sequence_no`,`deleted`),
  KEY `idx_rd_split_result_line_result` (`tenant_id`,`result_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `rd_order_split_output_bind` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT 1, `sale_order_id` bigint NOT NULL,
  `result_id` bigint NOT NULL, `result_line_id` bigint NOT NULL, `production_output_id` bigint NOT NULL,
  `output_qty_snapshot` decimal(24,6) NOT NULL, `status` varchar(16) NOT NULL DEFAULT 'ACTIVE',
  `binder_id` bigint DEFAULT NULL, `binder_name` varchar(64) DEFAULT NULL, `bind_time` datetime NOT NULL,
  `unbinder_id` bigint DEFAULT NULL, `unbinder_name` varchar(64) DEFAULT NULL, `unbind_time` datetime DEFAULT NULL,
  `unbind_reason` varchar(512) DEFAULT NULL, `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_split_bind_output` (`tenant_id`,`production_output_id`,`deleted`),
  KEY `idx_rd_split_bind_order` (`tenant_id`,`sale_order_id`,`status`,`deleted`),
  KEY `idx_rd_split_bind_line` (`tenant_id`,`result_line_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(600107,'生产炉层产量确认','rd:production-output:confirm',3,25,600101,'','','',NULL,0,b'1',b'1',b'1','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0'),
(600320,'订单拆分','rd:order-split:query',2,30,600300,'split','ep:share','rd/order/split/index','RdOrderSplit',0,b'1',b'1',b'1','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0'),
(600321,'订单拆分新增','rd:order-split:create',3,10,600320,'','','',NULL,0,b'1',b'1',b'1','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0'),
(600322,'订单拆分调整','rd:order-split:adjust',3,20,600320,'','','',NULL,0,b'1',b'1',b'1','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0'),
(600323,'订单拆分确认','rd:order-split:confirm',3,30,600320,'','','',NULL,0,b'1',b'1',b'1','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0'),
(600324,'订单拆分取消','rd:order-split:cancel',3,40,600320,'','','',NULL,0,b'1',b'1',b'1','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0'),
(600325,'订单拆分绑定','rd:order-split:bind',3,50,600320,'','','',NULL,0,b'1',b'1',b'1','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0'),
(600326,'拆分规则查询','rd:order-split:rule-query',3,60,600320,'','','',NULL,0,b'1',b'1',b'1','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0'),
(600327,'拆分规则管理','rd:order-split:rule-manage',3,70,600320,'','','',NULL,0,b'1',b'1',b'1','rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),
`sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`path`=VALUES(`path`),`icon`=VALUES(`icon`),
`component`=VALUES(`component`),`component_name`=VALUES(`component_name`),`deleted`=b'0',`update_time`=NOW();
INSERT IGNORE INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 600001,id,'rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0',1 FROM system_menu WHERE id=600107
UNION ALL SELECT 600003,id,'rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0',1 FROM system_menu WHERE id BETWEEN 600320 AND 600327
UNION ALL SELECT 600004,id,'rd-slim-migration',NOW(),'rd-slim-migration',NOW(),b'0',1 FROM system_menu WHERE id=600107 OR id BETWEEN 600320 AND 600327;

COMMIT;
DROP TEMPORARY TABLE IF EXISTS `rd_keep_menu_id`;
SET SQL_SAFE_UPDATES = 1;
