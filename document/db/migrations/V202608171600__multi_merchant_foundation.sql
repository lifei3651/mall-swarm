-- 可选多商户基座：现有商品默认仍为平台自营，不改变当前订单和奖金规则。
-- 商户货款按订单商品成本快照 × 有效数量结算；不在系统内计算“税费扣除”。

SET @schema_name = DATABASE();

CREATE TABLE IF NOT EXISTS dms_merchant (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  merchant_no VARCHAR(64) NOT NULL,
  merchant_name VARCHAR(128) NOT NULL,
  contact_name VARCHAR(64) NULL,
  contact_phone VARCHAR(32) NULL,
  settlement_mode VARCHAR(24) NOT NULL DEFAULT 'COST_PRICE',
  status TINYINT NOT NULL DEFAULT 1,
  remark VARCHAR(500) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_merchant_no (tenant_id, merchant_no),
  KEY idx_merchant_tenant_status (tenant_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户内商户';

CREATE TABLE IF NOT EXISTS dms_merchant_account (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  merchant_id BIGINT NOT NULL,
  pending_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
  available_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
  frozen_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
  debt_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
  total_paid_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_merchant_account (merchant_id),
  KEY idx_merchant_account_tenant (tenant_id, merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户货款账户';

CREATE TABLE IF NOT EXISTS dms_merchant_settlement (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  merchant_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  order_no VARCHAR(64) NOT NULL,
  order_item_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  sku_id BIGINT NULL,
  quantity INT NOT NULL,
  refunded_quantity INT NOT NULL DEFAULT 0,
  cost_amount DECIMAL(12,2) NOT NULL,
  settlement_amount DECIMAL(14,2) NOT NULL,
  reversed_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  available_time DATETIME NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_merchant_settlement_item (order_item_id),
  KEY idx_merchant_settlement_release (status, order_id, id),
  KEY idx_merchant_settlement_merchant (merchant_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按订单商品成本快照形成的商户货款';

CREATE TABLE IF NOT EXISTS dms_merchant_withdrawal (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  withdrawal_no VARCHAR(64) NOT NULL,
  merchant_id BIGINT NOT NULL,
  requested_amount DECIMAL(14,2) NOT NULL,
  invoice_required_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
  invoice_received_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
  invoice_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUIRED',
  adjustment_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
  adjustment_reason VARCHAR(256) NULL,
  actual_paid_amount DECIMAL(14,2) NULL,
  payment_reference VARCHAR(128) NULL,
  payment_voucher_url VARCHAR(512) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
  reject_reason VARCHAR(256) NULL,
  operator_id BIGINT NULL,
  operator_name VARCHAR(64) NULL,
  apply_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  paid_time DATETIME NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_merchant_withdrawal_no (withdrawal_no),
  KEY idx_merchant_withdrawal_search (tenant_id, merchant_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户货款提现、发票与人工打款';

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_product' AND COLUMN_NAME='merchant_id');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_product ADD COLUMN merchant_id BIGINT NULL COMMENT '空为平台自营' AFTER tenant_id",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_product' AND COLUMN_NAME='merchant_name');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_product ADD COLUMN merchant_name VARCHAR(128) NULL COMMENT '商户名称展示快照' AFTER merchant_id",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_product' AND COLUMN_NAME='enrollment_sale_enabled');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_product ADD COLUMN enrollment_sale_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '是否进入报单区' AFTER repurchase_purchase_limit",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_product' AND COLUMN_NAME='team_bonus_mode');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_product ADD COLUMN team_bonus_mode VARCHAR(16) NOT NULL DEFAULT 'INHERIT' COMMENT 'INHERIT/NONE/STANDARD/CUSTOM' AFTER enrollment_sale_enabled",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_order' AND COLUMN_NAME='merchant_id');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_order ADD COLUMN merchant_id BIGINT NULL AFTER tenant_id",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_order' AND COLUMN_NAME='merchant_name');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_order ADD COLUMN merchant_name VARCHAR(128) NULL AFTER merchant_id",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_order_item' AND COLUMN_NAME='merchant_id');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_order_item ADD COLUMN merchant_id BIGINT NULL AFTER order_no",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_order_item' AND COLUMN_NAME='merchant_name');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_order_item ADD COLUMN merchant_name VARCHAR(128) NULL AFTER merchant_id",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_order_item' AND COLUMN_NAME='team_bonus_mode');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_order_item ADD COLUMN team_bonus_mode VARCHAR(16) NOT NULL DEFAULT 'INHERIT' AFTER total_cost",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
