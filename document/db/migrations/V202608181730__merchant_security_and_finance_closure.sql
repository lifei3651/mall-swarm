-- 多商户安全与资金闭环：商户地址隔离、提现持久化防重复、收款资料快照和保证金目标。
-- 历史地址保持平台私有；历史提现保留原记录，新申请必须携带 request_no。
SET @schema_name = DATABASE();

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_service_address' AND COLUMN_NAME='merchant_id');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_service_address ADD COLUMN merchant_id BIGINT NULL COMMENT '空为平台地址，非空为商户私有地址' AFTER tenant_id",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_service_address' AND COLUMN_NAME='shared_to_merchants');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_service_address ADD COLUMN shared_to_merchants TINYINT NOT NULL DEFAULT 0 COMMENT '平台明确共享给商户' AFTER merchant_id",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_service_address' AND INDEX_NAME='idx_service_address_owner');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_service_address ADD INDEX idx_service_address_owner(tenant_id,merchant_id,address_type,status,is_default)",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_merchant' AND COLUMN_NAME='legal_entity_name');
SET @sql = IF(@exists=0,"ALTER TABLE dms_merchant ADD COLUMN legal_entity_name VARCHAR(128) NULL AFTER contact_phone",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_merchant' AND COLUMN_NAME='unified_social_credit_code');
SET @sql = IF(@exists=0,"ALTER TABLE dms_merchant ADD COLUMN unified_social_credit_code VARCHAR(32) NULL AFTER legal_entity_name",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_merchant' AND COLUMN_NAME='bank_account_name');
SET @sql = IF(@exists=0,"ALTER TABLE dms_merchant ADD COLUMN bank_account_name VARCHAR(128) NULL AFTER unified_social_credit_code",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_merchant' AND COLUMN_NAME='bank_name');
SET @sql = IF(@exists=0,"ALTER TABLE dms_merchant ADD COLUMN bank_name VARCHAR(128) NULL AFTER bank_account_name",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_merchant' AND COLUMN_NAME='bank_account_no');
SET @sql = IF(@exists=0,"ALTER TABLE dms_merchant ADD COLUMN bank_account_no VARCHAR(64) NULL AFTER bank_name",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_merchant' AND COLUMN_NAME='invoice_title');
SET @sql = IF(@exists=0,"ALTER TABLE dms_merchant ADD COLUMN invoice_title VARCHAR(128) NULL AFTER bank_account_no",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_merchant' AND COLUMN_NAME='taxpayer_identification_no');
SET @sql = IF(@exists=0,"ALTER TABLE dms_merchant ADD COLUMN taxpayer_identification_no VARCHAR(32) NULL AFTER invoice_title",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_merchant' AND COLUMN_NAME='contract_status');
SET @sql = IF(@exists=0,"ALTER TABLE dms_merchant ADD COLUMN contract_status VARCHAR(24) NOT NULL DEFAULT 'PENDING' AFTER taxpayer_identification_no",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_merchant' AND COLUMN_NAME='required_deposit_amount');
SET @sql = IF(@exists=0,"ALTER TABLE dms_merchant ADD COLUMN required_deposit_amount DECIMAL(14,2) NOT NULL DEFAULT 0 AFTER contract_status",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_merchant' AND COLUMN_NAME='profile_version');
SET @sql = IF(@exists=0,"ALTER TABLE dms_merchant ADD COLUMN profile_version INT NOT NULL DEFAULT 1 AFTER required_deposit_amount",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_merchant_withdrawal' AND COLUMN_NAME='request_no');
SET @sql = IF(@exists=0,"ALTER TABLE dms_merchant_withdrawal ADD COLUMN request_no VARCHAR(64) NULL COMMENT '客户端持久化防重复申请号' AFTER withdrawal_no",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_merchant_withdrawal' AND COLUMN_NAME='merchant_profile_version');
SET @sql = IF(@exists=0,"ALTER TABLE dms_merchant_withdrawal ADD COLUMN merchant_profile_version INT NULL AFTER merchant_id",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_merchant_withdrawal' AND COLUMN_NAME='legal_entity_name_snapshot');
SET @sql = IF(@exists=0,"ALTER TABLE dms_merchant_withdrawal ADD COLUMN legal_entity_name_snapshot VARCHAR(128) NULL AFTER merchant_profile_version",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_merchant_withdrawal' AND COLUMN_NAME='bank_account_name_snapshot');
SET @sql = IF(@exists=0,"ALTER TABLE dms_merchant_withdrawal ADD COLUMN bank_account_name_snapshot VARCHAR(128) NULL AFTER legal_entity_name_snapshot",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_merchant_withdrawal' AND COLUMN_NAME='bank_name_snapshot');
SET @sql = IF(@exists=0,"ALTER TABLE dms_merchant_withdrawal ADD COLUMN bank_name_snapshot VARCHAR(128) NULL AFTER bank_account_name_snapshot",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_merchant_withdrawal' AND COLUMN_NAME='bank_account_no_snapshot');
SET @sql = IF(@exists=0,"ALTER TABLE dms_merchant_withdrawal ADD COLUMN bank_account_no_snapshot VARCHAR(64) NULL AFTER bank_name_snapshot",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_merchant_withdrawal' AND INDEX_NAME='uk_merchant_withdrawal_request');
SET @sql = IF(@exists=0,"ALTER TABLE dms_merchant_withdrawal ADD UNIQUE INDEX uk_merchant_withdrawal_request(tenant_id,merchant_id,request_no)",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

