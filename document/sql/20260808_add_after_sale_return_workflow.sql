-- 退货退款流程字段：4=待寄回，5=客户已寄回，6=商家已收货待退款。
-- 仅新增字段，不删除或改写历史售后记录。
SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dms_shop_after_sale'
                  AND COLUMN_NAME = 'return_delivery_company') = 0,
               'ALTER TABLE `dms_shop_after_sale` ADD COLUMN `return_delivery_company` varchar(64) DEFAULT NULL AFTER `return_address`',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dms_shop_after_sale'
                  AND COLUMN_NAME = 'return_delivery_no') = 0,
               'ALTER TABLE `dms_shop_after_sale` ADD COLUMN `return_delivery_no` varchar(128) DEFAULT NULL AFTER `return_delivery_company`',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dms_shop_after_sale'
                  AND COLUMN_NAME = 'return_shipped_at') = 0,
               'ALTER TABLE `dms_shop_after_sale` ADD COLUMN `return_shipped_at` datetime DEFAULT NULL AFTER `return_delivery_no`',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dms_shop_after_sale'
                  AND COLUMN_NAME = 'return_received_at') = 0,
               'ALTER TABLE `dms_shop_after_sale` ADD COLUMN `return_received_at` datetime DEFAULT NULL AFTER `return_shipped_at`',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
