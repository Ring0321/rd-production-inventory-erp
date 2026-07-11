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
(600224, '客户型号选项查询', 'rd:customer-product:query', 3, 90, 600000, '', '', '', NULL, 0, b'0', b'0', b'0', 'rd-slim-migration', NOW(), 'rd-slim-migration', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `permission`=VALUES(`permission`), `type`=VALUES(`type`),
  `sort`=VALUES(`sort`), `parent_id`=VALUES(`parent_id`), `path`=VALUES(`path`), `icon`=VALUES(`icon`),
  `component`=VALUES(`component`), `component_name`=VALUES(`component_name`), `status`=0,
  `visible`=VALUES(`visible`), `keep_alive`=VALUES(`keep_alive`), `always_show`=VALUES(`always_show`),
  `updater`='rd-slim-migration', `update_time`=NOW(), `deleted`=b'0';

DELETE FROM `system_role_menu`
WHERE `tenant_id` = 1 AND `role_id` IN (600001,600002,600003,600004,600005);

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT 600001, id, 'rd-slim-migration', NOW(), 'rd-slim-migration', NOW(), b'0', 1 FROM `system_menu` WHERE id IN (600000,600010,600100,600101,600102,600103,600104,600105,600224,600500,600501)
UNION ALL SELECT 600002, id, 'rd-slim-migration', NOW(), 'rd-slim-migration', NOW(), b'0', 1 FROM `system_menu` WHERE id IN (600000,600010,600200,600201,600202,600203,600204,600205,600206,600207,600208,600209,600210,600211,600212,600213,600220,600221,600222,600223,600224,600400,600401,600402,600403,600404,600405,600406,600500,600501,600502,600503)
UNION ALL SELECT 600003, id, 'rd-slim-migration', NOW(), 'rd-slim-migration', NOW(), b'0', 1 FROM `system_menu` WHERE id IN (600000,600010,600224,600300,600301,600302,600303,600304,600305,600306,600310,600311,600312,600313,600400,600401,600402,600403,600404,600405,600406,600500,600501)
UNION ALL SELECT 600004, id, 'rd-slim-migration', NOW(), 'rd-slim-migration', NOW(), b'0', 1 FROM `system_menu` WHERE id IN (1,108,500,501,1040,1042,1043,1045,600000,600010,600100,600101,600200,600201,600203,600207,600213,600224,600300,600301,600310,600400,600401,600500,600501,600502,600503)
UNION ALL SELECT 600005, id, 'rd-slim-migration', NOW(), 'rd-slim-migration', NOW(), b'0', 1 FROM `system_menu` WHERE id IN (600000,600010,600100,600101,600200,600201,600203,600207,600213,600220,600224,600300,600301,600310,600400,600401,600500,600501);

COMMIT;
DROP TEMPORARY TABLE IF EXISTS `rd_keep_menu_id`;
SET SQL_SAFE_UPDATES = 1;
