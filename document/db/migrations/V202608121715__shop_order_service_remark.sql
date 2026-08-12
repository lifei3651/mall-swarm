-- 订单客服内部备注：与客户下单留言分离，默认不改变任何历史订单内容。

SET @schema_name = DATABASE();
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_order' AND COLUMN_NAME='service_remark');
SET @sql = IF(@exists=0,
  "ALTER TABLE dms_shop_order ADD COLUMN service_remark VARCHAR(500) NULL COMMENT '客服内部备注，不向客户展示' AFTER remark",
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
