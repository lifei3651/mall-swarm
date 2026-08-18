-- 跨商户一次支付：交易父单 + 商户履约子单。
SET @schema_name = DATABASE();

CREATE TABLE IF NOT EXISTS dms_shop_trade (
  id BIGINT NOT NULL COMMENT '交易父单ID',
  trade_no VARCHAR(64) NOT NULL COMMENT '一次支付交易号',
  tenant_id BIGINT NOT NULL DEFAULT 1,
  user_id BIGINT NOT NULL,
  pay_type VARCHAR(32) NOT NULL,
  total_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  freight_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  pay_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0待付款 1已支付 4已关闭',
  pay_time DATETIME NULL,
  close_time DATETIME NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_shop_trade_no (trade_no),
  KEY idx_shop_trade_user_status (tenant_id,user_id,status,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城一次结算支付父单';

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_order' AND COLUMN_NAME='trade_id');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_order ADD COLUMN trade_id BIGINT NULL COMMENT '跨商户交易父单ID' AFTER order_no",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_order' AND COLUMN_NAME='trade_no');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_order ADD COLUMN trade_no VARCHAR(64) NULL COMMENT '跨商户交易父单号' AFTER trade_id",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_order' AND COLUMN_NAME='payment_order_no');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_order ADD COLUMN payment_order_no VARCHAR(64) NULL COMMENT '支付渠道商户单号' AFTER trade_no",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE dms_shop_order SET payment_order_no=order_no WHERE payment_order_no IS NULL OR payment_order_no='';

SET @exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_order' AND INDEX_NAME='idx_shop_order_trade');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_order ADD INDEX idx_shop_order_trade(trade_id,id)",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_order' AND INDEX_NAME='idx_shop_order_payment_no');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_order ADD INDEX idx_shop_order_payment_no(payment_order_no)",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
