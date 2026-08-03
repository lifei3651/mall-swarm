-- 商城统一余额、余额支付、手机号转账和独立支付密码
-- 可在已有正式库重复执行。

SET @schema_name = DATABASE();

SET @sql = IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_member' AND COLUMN_NAME='pay_password_hash'),
  'SELECT 1',
  'ALTER TABLE dms_shop_member ADD COLUMN pay_password_hash varchar(128) DEFAULT NULL COMMENT ''独立支付密码哈希（BCrypt）'' AFTER salt'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_member' AND COLUMN_NAME='pay_password_failed_count'),
  'SELECT 1',
  'ALTER TABLE dms_shop_member ADD COLUMN pay_password_failed_count int NOT NULL DEFAULT 0 COMMENT ''连续支付密码错误次数'' AFTER pay_password_hash'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_member' AND COLUMN_NAME='pay_password_lock_time'),
  'SELECT 1',
  'ALTER TABLE dms_shop_member ADD COLUMN pay_password_lock_time datetime DEFAULT NULL COMMENT ''支付密码锁定时间'' AFTER pay_password_failed_count'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE dms_member_asset_account
SET asset_name='余额'
WHERE asset_code='CASH_BONUS';

UPDATE dms_member_asset_flow
SET asset_name='余额'
WHERE asset_code='CASH_BONUS';
