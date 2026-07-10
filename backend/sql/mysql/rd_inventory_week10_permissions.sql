-- Week 10 RD business roles and menu permissions for tenant 1.
-- Existing system administrators continue to use the built-in super_admin role.

SET NAMES utf8mb4;
SET @rd_role_tenant_id = 1;

INSERT INTO `system_role` (
  `id`, `name`, `code`, `sort`, `data_scope`, `data_scope_dept_ids`, `status`, `type`,
  `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`
) VALUES
(601001, '瑞鼎库管', 'rd_warehouse', 10, 1, '', 0, 2, '入库、出库、调拨、库存与日报', 'admin', NOW(), 'admin', NOW(), b'0', @rd_role_tenant_id),
(601002, '瑞鼎生产录入', 'rd_production', 20, 1, '', 0, 2, '生产产出录入、预检、查看', 'admin', NOW(), 'admin', NOW(), b'0', @rd_role_tenant_id),
(601003, '瑞鼎订单专员', 'rd_order', 30, 1, '', 0, 2, '销售订单、订单匹配与完成跟踪', 'admin', NOW(), 'admin', NOW(), b'0', @rd_role_tenant_id),
(601004, '瑞鼎审核员', 'rd_auditor', 40, 1, '', 0, 2, '库存调整、调拨确认与撤销审核', 'admin', NOW(), 'admin', NOW(), b'0', @rd_role_tenant_id),
(601005, '瑞鼎只读管理', 'rd_readonly', 50, 1, '', 0, 2, '看板、台账、订单、报表与导出只读权限', 'admin', NOW(), 'admin', NOW(), b'0', @rd_role_tenant_id)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `code` = VALUES(`code`),
  `sort` = VALUES(`sort`),
  `data_scope` = VALUES(`data_scope`),
  `status` = VALUES(`status`),
  `type` = VALUES(`type`),
  `remark` = VALUES(`remark`),
  `updater` = VALUES(`updater`),
  `update_time` = VALUES(`update_time`),
  `deleted` = b'0',
  `tenant_id` = VALUES(`tenant_id`);

DELETE FROM `system_role_menu`
WHERE `tenant_id` = @rd_role_tenant_id
  AND `role_id` IN (601001, 601002, 601003, 601004, 601005);

INSERT INTO `system_role_menu` (
  `role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`
)
SELECT 601001, `id`, 'admin', NOW(), 'admin', NOW(), b'0', @rd_role_tenant_id
FROM `system_menu`
WHERE `id` IN (
  600000, 600010,
  600100, 600101, 600106,
  600200, 600201, 600202, 600203, 600204, 600205, 600206,
  600207, 600208, 600209, 600210, 600211, 600212,
  600220, 600221, 600222, 600223,
  600300, 600301, 600310, 600311, 600312, 600313,
  600400, 600401, 600402, 600403, 600404, 600405, 600406,
  600500, 600501, 600502, 600503
)
UNION ALL
SELECT 601002, `id`, 'admin', NOW(), 'admin', NOW(), b'0', @rd_role_tenant_id
FROM `system_menu`
WHERE `id` IN (
  600000, 600010,
  600100, 600101, 600102, 600103, 600104, 600105,
  600200, 600220, 600221, 600223,
  600500, 600501
)
UNION ALL
SELECT 601003, `id`, 'admin', NOW(), 'admin', NOW(), b'0', @rd_role_tenant_id
FROM `system_menu`
WHERE `id` IN (
  600000, 600010,
  600200, 600201, 600203, 600207,
  600300, 600301, 600302, 600303, 600304, 600305, 600306,
  600310, 600311, 600312, 600313,
  600400, 600401,
  600500, 600501, 600503
)
UNION ALL
SELECT 601004, `id`, 'admin', NOW(), 'admin', NOW(), b'0', @rd_role_tenant_id
FROM `system_menu`
WHERE `id` IN (
  600000, 600010,
  600200, 600201, 600203, 600204, 600206, 600207,
  600300, 600301, 600310,
  600400, 600401, 600404, 600405, 600406,
  600500, 600501, 600502, 600503
)
UNION ALL
SELECT 601005, `id`, 'admin', NOW(), 'admin', NOW(), b'0', @rd_role_tenant_id
FROM `system_menu`
WHERE `id` IN (
  600000, 600010,
  600100, 600101, 600105,
  600200, 600201, 600202, 600203, 600204, 600207, 600212, 600220, 600223,
  600300, 600301, 600306, 600310,
  600400, 600401, 600406,
  600500, 600501, 600503
);
