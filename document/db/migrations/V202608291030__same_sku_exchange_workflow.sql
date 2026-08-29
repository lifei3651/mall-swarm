-- 通用同规格换货闭环。
-- 换货不改变原订单价格、奖金或结算口径；只记录退回与替换商品物流，并在发出替换商品时扣减可售库存。

SET @schema_name = DATABASE();

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_after_sale' AND COLUMN_NAME='exchange_delivery_company');
SET @sql = IF(@exists=0,
              "ALTER TABLE dms_shop_after_sale ADD COLUMN exchange_delivery_company VARCHAR(50) NULL COMMENT '换货替换商品物流公司' AFTER return_received_at",
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_after_sale' AND COLUMN_NAME='exchange_delivery_no');
SET @sql = IF(@exists=0,
              "ALTER TABLE dms_shop_after_sale ADD COLUMN exchange_delivery_no VARCHAR(64) NULL COMMENT '换货替换商品运单号' AFTER exchange_delivery_company",
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_after_sale' AND COLUMN_NAME='exchange_shipped_at');
SET @sql = IF(@exists=0,
              "ALTER TABLE dms_shop_after_sale ADD COLUMN exchange_shipped_at DATETIME NULL COMMENT '商家发出替换商品时间' AFTER exchange_delivery_no",
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_after_sale' AND COLUMN_NAME='exchange_received_at');
SET @sql = IF(@exists=0,
              "ALTER TABLE dms_shop_after_sale ADD COLUMN exchange_received_at DATETIME NULL COMMENT '会员确认或系统自动确认换货收货时间' AFTER exchange_shipped_at",
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_after_sale' AND INDEX_NAME='idx_after_sale_exchange_receipt');
SET @sql = IF(@exists=0,
              'CREATE INDEX idx_after_sale_exchange_receipt ON dms_shop_after_sale(status, exchange_shipped_at, id)',
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
