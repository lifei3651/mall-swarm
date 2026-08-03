-- 新零售等级只由有效订单、直属邀请和部门条件自动产生，商品不指定会员卡级。
-- 现网升级由部署脚本先切换新程序，再按 information_schema 判断并删除这两个历史字段。
SET @drop_distribution_enabled = IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dms_shop_product' AND COLUMN_NAME = 'distribution_enabled'),
  'ALTER TABLE `dms_shop_product` DROP COLUMN `distribution_enabled`',
  'SELECT 1'
);
PREPARE stmt FROM @drop_distribution_enabled;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop_activation_level = IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dms_shop_product' AND COLUMN_NAME = 'activation_level'),
  'ALTER TABLE `dms_shop_product` DROP COLUMN `activation_level`',
  'SELECT 1'
);
PREPARE stmt FROM @drop_activation_level;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
