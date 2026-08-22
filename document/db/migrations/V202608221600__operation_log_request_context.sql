-- 本版本尚未发布，首次上线前即采用可重复执行写法；即使 DDL 中途失败，核对后也可安全补跑。
SET @schema_name = DATABASE();

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_operation_log' AND COLUMN_NAME='ip_address');
SET @sql = IF(@exists=0,"ALTER TABLE dms_operation_log ADD COLUMN ip_address VARCHAR(64) NULL AFTER remark",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_operation_log' AND COLUMN_NAME='user_agent');
SET @sql = IF(@exists=0,"ALTER TABLE dms_operation_log ADD COLUMN user_agent VARCHAR(500) NULL AFTER ip_address",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_operation_log' AND COLUMN_NAME='request_id');
SET @sql = IF(@exists=0,"ALTER TABLE dms_operation_log ADD COLUMN request_id VARCHAR(64) NULL AFTER user_agent",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_operation_log' AND INDEX_NAME='idx_operation_log_request_id');
SET @sql = IF(@exists=0,"ALTER TABLE dms_operation_log ADD INDEX idx_operation_log_request_id(request_id)",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
