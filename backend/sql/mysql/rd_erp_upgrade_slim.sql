-- Ruiding ERP in-place slimming migration.
-- Run only against an isolated restore after backup verification.
-- This script preserves users, user-role links, roles, and all rd_* business rows.

SET NAMES utf8mb4;
SET SQL_SAFE_UPDATES = 0;
START TRANSACTION;

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
SET `name` = '瑞鼎生产库存订单ERP', `package_id` = 1,
    `status` = 0, `expire_time` = '2099-12-31 23:59:59',
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

COMMIT;
DROP TEMPORARY TABLE IF EXISTS `rd_keep_menu_id`;
SET SQL_SAFE_UPDATES = 1;
