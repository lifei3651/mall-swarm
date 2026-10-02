-- Configuration only: never assign or replace any existing member's inviter.
SET @schema_name = DATABASE();
SET @column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_tenant' AND COLUMN_NAME='default_inviter_enabled');
SET @column_sql = IF(@column_exists=0,"ALTER TABLE dms_tenant ADD COLUMN default_inviter_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '无分享新注册默认绑定主账号开关'",'SELECT 1');
PREPARE stmt FROM @column_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_tenant' AND COLUMN_NAME='default_inviter_code');
SET @column_sql = IF(@column_exists=0,"ALTER TABLE dms_tenant ADD COLUMN default_inviter_code VARCHAR(8) NULL COMMENT '指定普通商城主账号邀请码'",'SELECT 1');
PREPARE stmt FROM @column_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
