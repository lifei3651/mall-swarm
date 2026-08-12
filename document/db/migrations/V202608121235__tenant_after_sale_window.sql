-- 商城售后申请期限支持按客户配置两种起算方式。
-- 默认采用签收后起算；兼容旧业务时可在后台切换为下单后起算。
SET @schema_name = DATABASE();

SET @has_mode = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'dms_tenant'
    AND COLUMN_NAME = 'after_sale_window_mode'
);
SET @sql = IF(@has_mode = 0,
  "ALTER TABLE dms_tenant ADD COLUMN after_sale_window_mode varchar(32) NOT NULL DEFAULT 'RECEIVED' COMMENT '售后期限起算：RECEIVED-签收后，ORDER_CREATED-下单后' AFTER after_sale_policy",
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_days = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'dms_tenant'
    AND COLUMN_NAME = 'after_sale_window_days'
);
SET @sql = IF(@has_days = 0,
  "ALTER TABLE dms_tenant ADD COLUMN after_sale_window_days int NOT NULL DEFAULT 7 COMMENT '客户售后入口有效天数' AFTER after_sale_window_mode",
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE dms_tenant
SET after_sale_window_mode = 'RECEIVED', after_sale_window_days = 7
WHERE after_sale_window_mode IS NULL OR after_sale_window_mode NOT IN ('RECEIVED', 'ORDER_CREATED')
   OR after_sale_window_days IS NULL OR after_sale_window_days < 7 OR after_sale_window_days > 365;
