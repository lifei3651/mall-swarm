-- 商城经营主体、客服、备案与协议配置。可重复执行。
SET @schema_name = DATABASE();

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_tenant' AND COLUMN_NAME='company_address')=0,
  'ALTER TABLE dms_tenant ADD COLUMN company_address VARCHAR(255) NULL AFTER product_template', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_tenant' AND COLUMN_NAME='service_phone')=0,
  'ALTER TABLE dms_tenant ADD COLUMN service_phone VARCHAR(32) NULL AFTER company_address', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_tenant' AND COLUMN_NAME='service_email')=0,
  'ALTER TABLE dms_tenant ADD COLUMN service_email VARCHAR(128) NULL AFTER service_phone', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_tenant' AND COLUMN_NAME='icp_number')=0,
  'ALTER TABLE dms_tenant ADD COLUMN icp_number VARCHAR(128) NULL AFTER service_email', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_tenant' AND COLUMN_NAME='police_record_number')=0,
  'ALTER TABLE dms_tenant ADD COLUMN police_record_number VARCHAR(128) NULL AFTER icp_number', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_tenant' AND COLUMN_NAME='police_record_url')=0,
  'ALTER TABLE dms_tenant ADD COLUMN police_record_url VARCHAR(512) NULL AFTER police_record_number', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_tenant' AND COLUMN_NAME='business_license_url')=0,
  'ALTER TABLE dms_tenant ADD COLUMN business_license_url VARCHAR(512) NULL AFTER police_record_url', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_tenant' AND COLUMN_NAME='user_agreement')=0,
  'ALTER TABLE dms_tenant ADD COLUMN user_agreement LONGTEXT NULL AFTER business_license_url', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_tenant' AND COLUMN_NAME='privacy_policy')=0,
  'ALTER TABLE dms_tenant ADD COLUMN privacy_policy LONGTEXT NULL AFTER user_agreement', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_tenant' AND COLUMN_NAME='after_sale_policy')=0,
  'ALTER TABLE dms_tenant ADD COLUMN after_sale_policy LONGTEXT NULL AFTER privacy_policy', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
