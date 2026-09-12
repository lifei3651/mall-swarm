-- 商品评价回复：双方各一条，可各自修改；历史评价原文、星级及显隐状态不变。
-- 发布后端前先备份并经迁移总账执行；每列独立检测，支持重复执行。
SET @schema_name = DATABASE();

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_product_review' AND COLUMN_NAME='merchant_reply_version');
SET @sql = IF(@exists=0,
  "ALTER TABLE dms_shop_product_review ADD COLUMN merchant_reply_version INT NOT NULL DEFAULT 0 COMMENT '商家回复编辑版本'", 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_product_review' AND COLUMN_NAME='platform_reply_version');
SET @sql = IF(@exists=0,
  "ALTER TABLE dms_shop_product_review ADD COLUMN platform_reply_version INT NOT NULL DEFAULT 0 COMMENT '平台回复编辑版本'", 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_product_review' AND COLUMN_NAME='merchant_reply');
SET @sql = IF(@exists=0,
  "ALTER TABLE dms_shop_product_review ADD COLUMN merchant_reply VARCHAR(500) NULL COMMENT '商家公开回复'", 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_product_review' AND COLUMN_NAME='merchant_reply_by');
SET @sql = IF(@exists=0,
  "ALTER TABLE dms_shop_product_review ADD COLUMN merchant_reply_by BIGINT NULL COMMENT '商家回复后台账号'", 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_product_review' AND COLUMN_NAME='merchant_reply_time');
SET @sql = IF(@exists=0,
  "ALTER TABLE dms_shop_product_review ADD COLUMN merchant_reply_time DATETIME NULL COMMENT '商家回复更新时间'", 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_product_review' AND COLUMN_NAME='platform_reply');
SET @sql = IF(@exists=0,
  "ALTER TABLE dms_shop_product_review ADD COLUMN platform_reply VARCHAR(500) NULL COMMENT '平台公开回复'", 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_product_review' AND COLUMN_NAME='platform_reply_by');
SET @sql = IF(@exists=0,
  "ALTER TABLE dms_shop_product_review ADD COLUMN platform_reply_by BIGINT NULL COMMENT '平台回复后台账号'", 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_product_review' AND COLUMN_NAME='platform_reply_time');
SET @sql = IF(@exists=0,
  "ALTER TABLE dms_shop_product_review ADD COLUMN platform_reply_time DATETIME NULL COMMENT '平台回复更新时间'", 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
