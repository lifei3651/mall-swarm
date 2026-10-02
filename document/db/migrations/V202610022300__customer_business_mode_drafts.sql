-- Nullable means preserve the customer's 177 configuration. No data UPDATE or automatic mode assignment.
SET @schema_name = DATABASE();
SET @column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_tenant' AND COLUMN_NAME='business_mode');
SET @column_sql = IF(@column_exists=0,"ALTER TABLE dms_tenant ADD COLUMN business_mode VARCHAR(16) NULL COMMENT 'NORMAL/AGENCY; NULL沿用存量模式'",'SELECT 1');
PREPARE stmt FROM @column_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_tenant' AND COLUMN_NAME='agency_rule_draft');
SET @column_sql = IF(@column_exists=0,"ALTER TABLE dms_tenant ADD COLUMN agency_rule_draft TEXT NULL COMMENT '客户规则草稿，不启用资格或奖励'",'SELECT 1');
PREPARE stmt FROM @column_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
