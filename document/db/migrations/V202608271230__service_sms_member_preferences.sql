-- 服务短信会员偏好补充同意版本和来源端面。
-- 不保存手机号原文；现有 endpoint_hash 继续保存绑定手机号的 SHA-256 摘要。

SET @schema_name = DATABASE();

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_message_recipient_authorization' AND COLUMN_NAME='consent_version');
SET @sql = IF(@exists=0,
              "ALTER TABLE dms_message_recipient_authorization ADD COLUMN consent_version VARCHAR(64) NULL AFTER revoked_time",
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_message_recipient_authorization' AND COLUMN_NAME='consent_surface');
SET @sql = IF(@exists=0,
              "ALTER TABLE dms_message_recipient_authorization ADD COLUMN consent_surface VARCHAR(16) NULL AFTER consent_version",
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
