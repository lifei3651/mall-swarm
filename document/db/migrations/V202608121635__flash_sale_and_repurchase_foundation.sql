-- 秒杀与复购商城基座：全部能力默认关闭，不改变现有普通商品和订单。

SET @schema_name = DATABASE();

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_tenant' AND COLUMN_NAME='flash_sale_enabled');
SET @sql = IF(@exists=0,"ALTER TABLE dms_tenant ADD COLUMN flash_sale_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用秒杀模块'",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_tenant' AND COLUMN_NAME='flash_sale_bonus_mode');
SET @sql = IF(@exists=0,"ALTER TABLE dms_tenant ADD COLUMN flash_sale_bonus_mode VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT '秒杀奖金模式:NONE/STANDARD/CUSTOM'",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_tenant' AND COLUMN_NAME='repurchase_mall_enabled');
SET @sql = IF(@exists=0,"ALTER TABLE dms_tenant ADD COLUMN repurchase_mall_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用复购商城'",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_tenant' AND COLUMN_NAME='repurchase_eligibility_mode');
SET @sql = IF(@exists=0,"ALTER TABLE dms_tenant ADD COLUMN repurchase_eligibility_mode VARCHAR(24) NOT NULL DEFAULT 'PAID_MEMBER' COMMENT '复购准入:PAID_MEMBER/AGENT/ALL_MEMBER'",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_tenant' AND COLUMN_NAME='repurchase_bonus_mode');
SET @sql = IF(@exists=0,"ALTER TABLE dms_tenant ADD COLUMN repurchase_bonus_mode VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT '复购奖金模式:NONE/STANDARD/CUSTOM'",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_product' AND COLUMN_NAME='normal_sale_enabled');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_product ADD COLUMN normal_sale_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否进入普通商城'",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_product' AND COLUMN_NAME='repurchase_sale_enabled');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_product ADD COLUMN repurchase_sale_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '是否进入复购商城'",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_product' AND COLUMN_NAME='repurchase_price');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_product ADD COLUMN repurchase_price DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '复购价'",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_product' AND COLUMN_NAME='repurchase_pv');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_product ADD COLUMN repurchase_pv DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '复购PV'",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_product' AND COLUMN_NAME='repurchase_purchase_limit');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_product ADD COLUMN repurchase_purchase_limit INT NOT NULL DEFAULT 0 COMMENT '每位会员累计复购限购,0不限'",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_sku' AND COLUMN_NAME='repurchase_price');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_sku ADD COLUMN repurchase_price DECIMAL(12,2) NULL COMMENT 'SKU复购价,空则继承商品'",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_sku' AND COLUMN_NAME='repurchase_pv');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_sku ADD COLUMN repurchase_pv DECIMAL(12,2) NULL COMMENT 'SKU复购PV,空则继承商品'",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_order' AND COLUMN_NAME='business_type');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_order ADD COLUMN business_type VARCHAR(24) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/FLASH_SALE/REPURCHASE'",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_order' AND COLUMN_NAME='business_source_id');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_order ADD COLUMN business_source_id BIGINT NULL COMMENT '秒杀活动等业务来源ID'",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS dms_flash_sale_activity (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  activity_name VARCHAR(80) NOT NULL,
  product_id BIGINT NOT NULL,
  sku_id BIGINT NULL,
  flash_price DECIMAL(12,2) NOT NULL,
  flash_pv DECIMAL(12,2) NOT NULL DEFAULT 0,
  total_stock INT NOT NULL,
  available_stock INT NOT NULL,
  per_user_limit INT NOT NULL DEFAULT 1,
  start_time DATETIME NOT NULL,
  end_time DATETIME NOT NULL,
  status TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_flash_tenant_time (tenant_id, status, start_time, end_time),
  KEY idx_flash_product (tenant_id, product_id, sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀活动';

CREATE TABLE IF NOT EXISTS dms_flash_sale_reservation (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  activity_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  order_id BIGINT NULL,
  order_no VARCHAR(64) NULL,
  quantity INT NOT NULL,
  released_quantity INT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'RESERVED',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_flash_member (activity_id, user_id),
  UNIQUE KEY uk_flash_order (order_id),
  KEY idx_flash_reservation_status (tenant_id, activity_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀抢购资格与订单绑定';

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_flash_sale_reservation' AND COLUMN_NAME='released_quantity');
SET @sql = IF(@exists=0,"ALTER TABLE dms_flash_sale_reservation ADD COLUMN released_quantity INT NOT NULL DEFAULT 0 AFTER quantity",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_order' AND INDEX_NAME='idx_shop_order_business_type');
SET @sql = IF(@exists=0,'ALTER TABLE dms_shop_order ADD INDEX idx_shop_order_business_type (tenant_id, business_type, create_time, id)','SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
