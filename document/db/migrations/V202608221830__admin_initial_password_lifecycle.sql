-- 后台账号初始密码生命周期：已有账号不强制改密；新建或重置后的账号由应用写入 1。
SET @schema_name = DATABASE();
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_admin_user' AND COLUMN_NAME='must_change_password');
SET @sql = IF(@exists=0,"ALTER TABLE dms_admin_user ADD COLUMN must_change_password TINYINT NOT NULL DEFAULT 0 COMMENT '是否必须先修改后台初始密码：0否 1是' AFTER lock_time",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
