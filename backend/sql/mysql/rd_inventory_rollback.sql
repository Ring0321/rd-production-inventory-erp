-- Rollback for RD production inventory module schema and menu seed.
-- Execute only when removing the RD module.

DELETE FROM `system_menu` WHERE `id` BETWEEN 600000 AND 600999;

DROP TABLE IF EXISTS `rd_excel_import_error`;
DROP TABLE IF EXISTS `rd_excel_import_task`;
DROP TABLE IF EXISTS `rd_daily_stock_summary`;
DROP TABLE IF EXISTS `rd_transfer_order`;
DROP TABLE IF EXISTS `rd_order_fulfillment`;
DROP TABLE IF EXISTS `rd_sale_order_extend`;
DROP TABLE IF EXISTS `rd_stock_transaction`;
DROP TABLE IF EXISTS `rd_stock_lot`;
DROP TABLE IF EXISTS `rd_production_output`;
DROP TABLE IF EXISTS `rd_furnace_layer`;
DROP TABLE IF EXISTS `rd_location`;
