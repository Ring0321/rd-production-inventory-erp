-- Week 10 query-index upgrade for an existing RD inventory schema.
-- Back up the database before running this script in a non-local environment.

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `rd_rebuild_query_index`;
DELIMITER $$
CREATE PROCEDURE `rd_rebuild_query_index`(
  IN table_name_value varchar(64),
  IN index_name_value varchar(64),
  IN expected_columns varchar(512)
)
BEGIN
  DECLARE actual_columns varchar(512);
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
    INTO actual_columns
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = table_name_value
    AND index_name = index_name_value;

  IF actual_columns IS NULL THEN
    SET @index_sql = CONCAT(
      'ALTER TABLE `', table_name_value, '` ADD INDEX `', index_name_value,
      '` (`', REPLACE(expected_columns, ',', '`,`'), '`)'
    );
    PREPARE index_stmt FROM @index_sql;
    EXECUTE index_stmt;
    DEALLOCATE PREPARE index_stmt;
  ELSEIF actual_columns <> expected_columns THEN
    SET @index_sql = CONCAT(
      'ALTER TABLE `', table_name_value, '` DROP INDEX `', index_name_value,
      '`, ADD INDEX `', index_name_value, '` (`',
      REPLACE(expected_columns, ',', '`,`'), '`)'
    );
    PREPARE index_stmt FROM @index_sql;
    EXECUTE index_stmt;
    DEALLOCATE PREPARE index_stmt;
  END IF;
END$$
DELIMITER ;

CALL `rd_rebuild_query_index`('rd_production_output', 'idx_rd_output_date', 'tenant_id,deleted,production_date,id');
CALL `rd_rebuild_query_index`('rd_production_output', 'idx_rd_output_core', 'tenant_id,customer_name,product_model,furnace_no,furnace_layer,location_code,deleted');

CALL `rd_rebuild_query_index`('rd_stock_lot', 'idx_rd_stock_lot_core', 'tenant_id,customer_name,product_model,furnace_no,furnace_layer,location_code,deleted');
CALL `rd_rebuild_query_index`('rd_stock_lot', 'idx_rd_stock_lot_match', 'tenant_id,product_model,status,deleted,last_biz_date,id');
CALL `rd_rebuild_query_index`('rd_stock_lot', 'idx_rd_stock_lot_customer_match', 'tenant_id,customer_name,product_model,status,deleted,last_biz_date,id');
CALL `rd_rebuild_query_index`('rd_stock_lot', 'idx_rd_stock_lot_location', 'tenant_id,location_id,status,deleted');
CALL `rd_rebuild_query_index`('rd_stock_lot', 'idx_rd_stock_lot_status_date', 'tenant_id,deleted,status,last_biz_date,id');
CALL `rd_rebuild_query_index`('rd_stock_lot', 'idx_rd_stock_lot_timeline', 'tenant_id,deleted,last_biz_date,id,status');
CALL `rd_rebuild_query_index`('rd_stock_lot', 'idx_rd_stock_lot_status_qty', 'tenant_id,status,deleted,warehouse_qty,locked_qty,stock_weight');

CALL `rd_rebuild_query_index`('rd_stock_transaction', 'idx_rd_stock_txn_date_type', 'tenant_id,biz_date,transaction_type,deleted,id');
CALL `rd_rebuild_query_index`('rd_stock_transaction', 'idx_rd_stock_txn_lot_time', 'tenant_id,stock_lot_id,deleted,create_time,id');
CALL `rd_rebuild_query_index`('rd_stock_transaction', 'idx_rd_stock_txn_source', 'tenant_id,source_type,source_no,deleted');
CALL `rd_rebuild_query_index`('rd_stock_transaction', 'idx_rd_stock_txn_order', 'tenant_id,related_order_id,deleted,biz_date,id');

CALL `rd_rebuild_query_index`('rd_sale_order_extend', 'idx_rd_sale_order_date_status', 'tenant_id,deleted,order_date,id,status');
CALL `rd_rebuild_query_index`('rd_sale_order_extend', 'idx_rd_sale_order_customer_status', 'tenant_id,customer_name,product_model,status,deleted,order_date,id');
CALL `rd_rebuild_query_index`('rd_sale_order_extend', 'idx_rd_sale_order_status_date', 'tenant_id,status,deleted,order_date,id');

CALL `rd_rebuild_query_index`('rd_order_fulfillment', 'idx_rd_fulfillment_order_lot', 'tenant_id,order_id,stock_lot_id,status,deleted');
CALL `rd_rebuild_query_index`('rd_order_fulfillment', 'idx_rd_fulfillment_status', 'tenant_id,status,deleted');
CALL `rd_rebuild_query_index`('rd_order_fulfillment', 'idx_rd_fulfillment_lot_status', 'tenant_id,stock_lot_id,status,deleted');

CALL `rd_rebuild_query_index`('rd_transfer_order', 'idx_rd_transfer_date_status', 'tenant_id,deleted,transfer_date,id,status');
CALL `rd_rebuild_query_index`('rd_transfer_order', 'idx_rd_transfer_link', 'tenant_id,to_order_id,from_stock_lot_id,status,deleted');

CALL `rd_rebuild_query_index`('rd_outbound_order', 'idx_rd_outbound_date_status', 'tenant_id,deleted,outbound_date,id,status');
CALL `rd_rebuild_query_index`('rd_outbound_order', 'idx_rd_outbound_order_fulfillment', 'tenant_id,order_id,fulfillment_id,status,deleted');
CALL `rd_rebuild_query_index`('rd_outbound_order', 'idx_rd_outbound_fulfillment_status', 'tenant_id,fulfillment_id,status,deleted');

CALL `rd_rebuild_query_index`('rd_daily_stock_summary', 'idx_rd_daily_summary_query', 'tenant_id,deleted,biz_date,customer_name,product_model,location_code');
CALL `rd_rebuild_query_index`('rd_excel_import_task', 'idx_rd_import_task_status', 'tenant_id,status,deleted,create_time,id');
CALL `rd_rebuild_query_index`('rd_excel_import_row', 'idx_rd_import_row_task_status', 'tenant_id,task_id,status,deleted,row_no');
CALL `rd_rebuild_query_index`('rd_excel_import_error', 'idx_rd_import_error_task', 'tenant_id,task_id,deleted,row_no,id');

DROP PROCEDURE `rd_rebuild_query_index`;

DROP PROCEDURE IF EXISTS `rd_rebuild_unique_query_index`;
DELIMITER $$
CREATE PROCEDURE `rd_rebuild_unique_query_index`(
  IN table_name_value varchar(64),
  IN index_name_value varchar(64),
  IN expected_columns varchar(512)
)
BEGIN
  DECLARE actual_columns varchar(512);
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

CALL `rd_rebuild_unique_query_index`('rd_daily_stock_summary', 'uk_rd_daily_summary', 'tenant_id,deleted,biz_date,stock_lot_id');

DROP PROCEDURE `rd_rebuild_unique_query_index`;
