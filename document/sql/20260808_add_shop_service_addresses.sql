-- 商城经营地址簿：1=发货地址，2=退货地址。
-- 仅新增结构和商品/售后地址快照字段，不删除或改写业务数据。
CREATE TABLE IF NOT EXISTS `dms_shop_service_address` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 1,
  `address_type` tinyint NOT NULL COMMENT '1发货地址 2退货地址',
  `address_label` varchar(64) DEFAULT NULL,
  `contact_name` varchar(64) NOT NULL,
  `contact_phone` varchar(32) NOT NULL,
  `province` varchar(64) NOT NULL,
  `city` varchar(64) NOT NULL,
  `district` varchar(64) NOT NULL,
  `detail_address` varchar(255) NOT NULL,
  `is_default` tinyint NOT NULL DEFAULT 0,
  `status` tinyint NOT NULL DEFAULT 1,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_shop_service_address_tenant_type` (`tenant_id`, `address_type`, `status`, `is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城发货与退货地址';

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dms_shop_product'
                  AND COLUMN_NAME = 'shipping_address_id') = 0,
               'ALTER TABLE `dms_shop_product` ADD COLUMN `shipping_address_id` bigint DEFAULT NULL AFTER `delivery_district`',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dms_shop_product'
                  AND COLUMN_NAME = 'return_address_id') = 0,
               'ALTER TABLE `dms_shop_product` ADD COLUMN `return_address_id` bigint DEFAULT NULL AFTER `shipping_address_id`',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dms_shop_after_sale'
                  AND COLUMN_NAME = 'return_address_id') = 0,
               'ALTER TABLE `dms_shop_after_sale` ADD COLUMN `return_address_id` bigint DEFAULT NULL AFTER `proof_images`',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dms_shop_after_sale'
                  AND COLUMN_NAME = 'return_address') = 0,
               'ALTER TABLE `dms_shop_after_sale` ADD COLUMN `return_address` varchar(512) DEFAULT NULL AFTER `return_address_id`',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
