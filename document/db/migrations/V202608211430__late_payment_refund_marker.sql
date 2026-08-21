-- 支付宝在本地超时关单后才成功扣款时，保存本地退款幂等标记，避免渠道重复通知形成重试循环。
SET @schema_name = DATABASE();

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_trade' AND COLUMN_NAME='late_refund_flag');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_trade ADD COLUMN late_refund_flag TINYINT NOT NULL DEFAULT 0 COMMENT '超时关单后的迟到支付是否已原路退款' AFTER status",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_order' AND COLUMN_NAME='late_refund_flag');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_order ADD COLUMN late_refund_flag TINYINT NOT NULL DEFAULT 0 COMMENT '超时关单后的迟到支付是否已原路退款' AFTER pay_type",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
