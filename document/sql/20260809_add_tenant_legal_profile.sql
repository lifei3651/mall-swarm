-- 协议主体与隐私政策资料补充（幂等迁移，不修改或删除现有业务数据）
SET @schema_name = DATABASE();

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_tenant' AND COLUMN_NAME='unified_social_credit_code')=0,
  'ALTER TABLE dms_tenant ADD COLUMN unified_social_credit_code VARCHAR(32) NULL COMMENT ''统一社会信用代码'' AFTER company_address', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_tenant' AND COLUMN_NAME='service_hours')=0,
  'ALTER TABLE dms_tenant ADD COLUMN service_hours VARCHAR(128) NULL COMMENT ''客服工作时间'' AFTER service_email', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_tenant' AND COLUMN_NAME='third_party_services')=0,
  'ALTER TABLE dms_tenant ADD COLUMN third_party_services TEXT NULL COMMENT ''隐私政策所需的第三方服务清单'' AFTER service_hours', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
