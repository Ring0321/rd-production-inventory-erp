-- Adds tenant isolation to an existing RD inventory schema.
-- Run once after backing up the database. Existing RD rows are assigned to tenant 1.

SET NAMES utf8mb4;
SET @rd_existing_tenant_id = 1;

DROP PROCEDURE IF EXISTS `rd_add_tenant_column`;
DELIMITER $$
CREATE PROCEDURE `rd_add_tenant_column`(IN table_name_value varchar(64))
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = table_name_value
      AND column_name = 'tenant_id'
  ) THEN
    SET @alter_sql = CONCAT(
      'ALTER TABLE `', table_name_value,
      '` ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT ''Tenant ID'' AFTER `id`'
    );
    PREPARE alter_stmt FROM @alter_sql;
    EXECUTE alter_stmt;
    DEALLOCATE PREPARE alter_stmt;
  END IF;
END$$
DELIMITER ;

CALL `rd_add_tenant_column`('rd_location');
CALL `rd_add_tenant_column`('rd_furnace_layer');
CALL `rd_add_tenant_column`('rd_production_output');
CALL `rd_add_tenant_column`('rd_stock_lot');
CALL `rd_add_tenant_column`('rd_stock_transaction');
CALL `rd_add_tenant_column`('rd_sale_order_extend');
CALL `rd_add_tenant_column`('rd_order_fulfillment');
CALL `rd_add_tenant_column`('rd_transfer_order');
CALL `rd_add_tenant_column`('rd_outbound_order');
CALL `rd_add_tenant_column`('rd_daily_stock_summary');
CALL `rd_add_tenant_column`('rd_excel_import_task');
CALL `rd_add_tenant_column`('rd_excel_import_row');
CALL `rd_add_tenant_column`('rd_excel_import_error');

DROP PROCEDURE `rd_add_tenant_column`;

UPDATE `rd_location` SET `tenant_id` = @rd_existing_tenant_id WHERE `tenant_id` = 0;
UPDATE `rd_furnace_layer` SET `tenant_id` = @rd_existing_tenant_id WHERE `tenant_id` = 0;
UPDATE `rd_production_output` SET `tenant_id` = @rd_existing_tenant_id WHERE `tenant_id` = 0;
UPDATE `rd_stock_lot` SET `tenant_id` = @rd_existing_tenant_id WHERE `tenant_id` = 0;
UPDATE `rd_stock_transaction` SET `tenant_id` = @rd_existing_tenant_id WHERE `tenant_id` = 0;
UPDATE `rd_sale_order_extend` SET `tenant_id` = @rd_existing_tenant_id WHERE `tenant_id` = 0;
UPDATE `rd_order_fulfillment` SET `tenant_id` = @rd_existing_tenant_id WHERE `tenant_id` = 0;
UPDATE `rd_transfer_order` SET `tenant_id` = @rd_existing_tenant_id WHERE `tenant_id` = 0;
UPDATE `rd_outbound_order` SET `tenant_id` = @rd_existing_tenant_id WHERE `tenant_id` = 0;
UPDATE `rd_daily_stock_summary` SET `tenant_id` = @rd_existing_tenant_id WHERE `tenant_id` = 0;
UPDATE `rd_excel_import_task` SET `tenant_id` = @rd_existing_tenant_id WHERE `tenant_id` = 0;
UPDATE `rd_excel_import_row` SET `tenant_id` = @rd_existing_tenant_id WHERE `tenant_id` = 0;
UPDATE `rd_excel_import_error` SET `tenant_id` = @rd_existing_tenant_id WHERE `tenant_id` = 0;

DROP PROCEDURE IF EXISTS `rd_rebuild_tenant_unique_index`;
DELIMITER $$
CREATE PROCEDURE `rd_rebuild_tenant_unique_index`(
  IN table_name_value varchar(64),
  IN index_name_value varchar(64),
  IN expected_columns varchar(255)
)
BEGIN
  DECLARE actual_columns varchar(255);
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
    INTO actual_columns
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = table_name_value
    AND index_name = index_name_value;

  IF actual_columns IS NULL THEN
    SET @index_sql = CONCAT(
      'ALTER TABLE `', table_name_value, '` ADD UNIQUE INDEX `', index_name_value,
      '` (`', REPLACE(expected_columns, ',', '`,`'), '`)'
    );
    PREPARE index_stmt FROM @index_sql;
    EXECUTE index_stmt;
    DEALLOCATE PREPARE index_stmt;
  ELSEIF actual_columns <> expected_columns THEN
    SET @index_sql = CONCAT(
      'ALTER TABLE `', table_name_value, '` DROP INDEX `', index_name_value,
      '`, ADD UNIQUE INDEX `', index_name_value, '` (`',
      REPLACE(expected_columns, ',', '`,`'), '`)'
    );
    PREPARE index_stmt FROM @index_sql;
    EXECUTE index_stmt;
    DEALLOCATE PREPARE index_stmt;
  END IF;
END$$
DELIMITER ;

CALL `rd_rebuild_tenant_unique_index`('rd_location', 'uk_rd_location_code', 'tenant_id,location_code,deleted');
CALL `rd_rebuild_tenant_unique_index`('rd_furnace_layer', 'uk_rd_furnace_layer_code', 'tenant_id,layer_code,deleted');
CALL `rd_rebuild_tenant_unique_index`('rd_sale_order_extend', 'uk_rd_sale_order_no', 'tenant_id,order_no,deleted');
CALL `rd_rebuild_tenant_unique_index`('rd_transfer_order', 'uk_rd_transfer_no', 'tenant_id,transfer_no,deleted');
CALL `rd_rebuild_tenant_unique_index`('rd_outbound_order', 'uk_rd_outbound_no', 'tenant_id,outbound_no,deleted');
CALL `rd_rebuild_tenant_unique_index`('rd_daily_stock_summary', 'uk_rd_daily_summary', 'tenant_id,biz_date,stock_lot_id,deleted');
CALL `rd_rebuild_tenant_unique_index`('rd_excel_import_row', 'uk_rd_import_row', 'tenant_id,task_id,row_no,deleted');

DROP PROCEDURE `rd_rebuild_tenant_unique_index`;
