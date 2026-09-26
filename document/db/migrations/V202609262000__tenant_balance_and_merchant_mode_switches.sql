-- Existing customers retain both capabilities. New customers are created through TenantServiceImpl,
-- which explicitly starts in platform-only mode; the SQL default protects legacy insert paths.
SET @schema_name = DATABASE();
SET @balance_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_tenant' AND COLUMN_NAME='balance_transactions_enabled');
SET @balance_sql = IF(@balance_exists=0,"ALTER TABLE dms_tenant ADD COLUMN balance_transactions_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许新增余额交易'",'SELECT 1');
PREPARE stmt FROM @balance_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @merchant_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_tenant' AND COLUMN_NAME='multi_merchant_enabled');
SET @merchant_sql = IF(@merchant_exists=0,"ALTER TABLE dms_tenant ADD COLUMN multi_merchant_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许新增多商户经营'",'SELECT 1');
PREPARE stmt FROM @merchant_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
