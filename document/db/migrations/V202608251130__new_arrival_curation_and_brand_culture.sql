-- 1.0.86 新品运营追加与独立品牌文化页。
-- 手动新品到期只退出新品页，不修改商品上下架、分类或普通销售状态。

SET @schema_name = DATABASE();

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_product' AND COLUMN_NAME='manual_new_arrival_enabled');
SET @sql = IF(@exists=0,
              "ALTER TABLE dms_shop_product ADD COLUMN manual_new_arrival_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '运营手动追加新品' AFTER first_publish_time",
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_product' AND COLUMN_NAME='manual_new_arrival_start_time');
SET @sql = IF(@exists=0,
              "ALTER TABLE dms_shop_product ADD COLUMN manual_new_arrival_start_time DATETIME NULL COMMENT '运营新品展示开始时间' AFTER manual_new_arrival_enabled",
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_product' AND COLUMN_NAME='manual_new_arrival_end_time');
SET @sql = IF(@exists=0,
              "ALTER TABLE dms_shop_product ADD COLUMN manual_new_arrival_end_time DATETIME NULL COMMENT '运营新品展示结束时间；空为永久' AFTER manual_new_arrival_start_time",
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_product' AND INDEX_NAME='idx_shop_product_manual_new_arrival');
SET @sql = IF(@exists=0,
              'CREATE INDEX idx_shop_product_manual_new_arrival ON dms_shop_product (tenant_id, status, normal_sale_enabled, manual_new_arrival_enabled, manual_new_arrival_end_time, id)',
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_tenant' AND COLUMN_NAME='brand_culture_enabled');
SET @sql = IF(@exists=0,
              "ALTER TABLE dms_tenant ADD COLUMN brand_culture_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '品牌文化页公开开关' AFTER product_template",
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_tenant' AND COLUMN_NAME='brand_culture_title');
SET @sql = IF(@exists=0,
              'ALTER TABLE dms_tenant ADD COLUMN brand_culture_title VARCHAR(80) NULL AFTER brand_culture_enabled',
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_tenant' AND COLUMN_NAME='brand_culture_subtitle');
SET @sql = IF(@exists=0,
              'ALTER TABLE dms_tenant ADD COLUMN brand_culture_subtitle VARCHAR(200) NULL AFTER brand_culture_title',
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_tenant' AND COLUMN_NAME='brand_culture_cover_url');
SET @sql = IF(@exists=0,
              'ALTER TABLE dms_tenant ADD COLUMN brand_culture_cover_url VARCHAR(2048) NULL AFTER brand_culture_subtitle',
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_tenant' AND COLUMN_NAME='brand_culture_content');
SET @sql = IF(@exists=0,
              'ALTER TABLE dms_tenant ADD COLUMN brand_culture_content TEXT NULL AFTER brand_culture_cover_url',
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
