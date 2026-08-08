-- 商品与 SKU 安全库存，0 表示不设置低库存阈值。
SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dms_shop_product'
                  AND COLUMN_NAME = 'safety_stock') = 0,
               'ALTER TABLE `dms_shop_product` ADD COLUMN `safety_stock` int NOT NULL DEFAULT 0 AFTER `stock`',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dms_shop_sku'
                  AND COLUMN_NAME = 'safety_stock') = 0,
               'ALTER TABLE `dms_shop_sku` ADD COLUMN `safety_stock` int NOT NULL DEFAULT 0 AFTER `stock`',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
