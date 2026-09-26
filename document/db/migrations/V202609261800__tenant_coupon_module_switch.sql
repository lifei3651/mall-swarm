-- 现有客户保持优惠券开启；新交付项目可在业务模式中独立关闭。
SET @schema_name = DATABASE();
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_tenant' AND COLUMN_NAME='coupon_enabled');
SET @sql = IF(@exists=0,"ALTER TABLE dms_tenant ADD COLUMN coupon_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用优惠券模块'",'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
