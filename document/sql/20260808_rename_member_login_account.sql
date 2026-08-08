-- 商城会员“登录账号”字段统一。
-- 仅修改 dms_shop_member 的字段名称，不修改任何已有账号值。
-- 管理员账号使用独立的 dms_admin_user.username，不属于本迁移范围。

SET @has_username := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'dms_shop_member'
    AND COLUMN_NAME = 'username'
);
SET @has_login_account := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'dms_shop_member'
    AND COLUMN_NAME = 'login_account'
);

SET @sql := IF(
  @has_username = 1 AND @has_login_account = 0,
  'ALTER TABLE `dms_shop_member` CHANGE COLUMN `username` `login_account` varchar(64) DEFAULT NULL COMMENT ''登录账号''',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 兼容曾经手工添加过 login_account 的非标准环境：只补齐空值，不覆盖已存在的新字段值。
SET @has_username := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'dms_shop_member'
    AND COLUMN_NAME = 'username'
);
SET @has_login_account := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'dms_shop_member'
    AND COLUMN_NAME = 'login_account'
);
SET @sql := IF(
  @has_username = 1 AND @has_login_account = 1,
  'UPDATE `dms_shop_member` SET `login_account` = `username` WHERE (`login_account` IS NULL OR `login_account` = '''') AND `username` IS NOT NULL',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- MySQL 修改列名会保留原索引名称，这里同步把索引名改成登录账号语义。
SET @has_old_index := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'dms_shop_member'
    AND INDEX_NAME = 'uk_username'
);
SET @has_new_index := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'dms_shop_member'
    AND INDEX_NAME = 'uk_login_account'
);
SET @sql := IF(
  @has_username = 0 AND @has_login_account = 1
    AND @has_old_index > 0 AND @has_new_index = 0,
  'ALTER TABLE `dms_shop_member` RENAME INDEX `uk_username` TO `uk_login_account`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
