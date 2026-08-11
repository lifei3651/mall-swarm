-- 订单/售后实时刷新后的高频查询组合索引（MySQL 8，幂等执行）
-- 只新增索引，不删除或修改业务数据。

SET @schema_name = DATABASE();

SET @exists_count = (SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema=@schema_name AND table_name='dms_shop_order' AND index_name='idx_order_user_status_time');
SET @ddl = IF(@exists_count=0,
  'ALTER TABLE dms_shop_order ADD INDEX idx_order_user_status_time (user_id, status, create_time, id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists_count = (SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema=@schema_name AND table_name='dms_shop_order' AND index_name='idx_order_tenant_status_time');
SET @ddl = IF(@exists_count=0,
  'ALTER TABLE dms_shop_order ADD INDEX idx_order_tenant_status_time (tenant_id, status, create_time, id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists_count = (SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema=@schema_name AND table_name='dms_shop_after_sale' AND index_name='idx_after_sale_order_status');
SET @ddl = IF(@exists_count=0,
  'ALTER TABLE dms_shop_after_sale ADD INDEX idx_after_sale_order_status (order_id, status, id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists_count = (SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema=@schema_name AND table_name='dms_shop_after_sale' AND index_name='idx_after_sale_member_status_time');
SET @ddl = IF(@exists_count=0,
  'ALTER TABLE dms_shop_after_sale ADD INDEX idx_after_sale_member_status_time (member_id, status, create_time, id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
