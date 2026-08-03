-- 售后按商品明细/实际件数退款，并拆分商品款与运费。
-- 旧退款无法还原商品件数，因此仅回填商品退款金额；新退款必须写入售后商品明细。

SET @schema_name = DATABASE();

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_finance_refund' AND COLUMN_NAME='product_refund_amount')=0,
  'ALTER TABLE dms_finance_refund ADD COLUMN product_refund_amount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT ''商品退款金额'' AFTER refund_amount', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_finance_refund' AND COLUMN_NAME='freight_refund_amount')=0,
  'ALTER TABLE dms_finance_refund ADD COLUMN freight_refund_amount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT ''运费退款金额'' AFTER product_refund_amount', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_finance_refund' AND COLUMN_NAME='refund_quantity')=0,
  'ALTER TABLE dms_finance_refund ADD COLUMN refund_quantity INT NOT NULL DEFAULT 0 COMMENT ''实际退回商品件数'' AFTER freight_refund_amount', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_after_sale' AND COLUMN_NAME='product_refund_amount')=0,
  'ALTER TABLE dms_shop_after_sale ADD COLUMN product_refund_amount DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT ''商品退款金额'' AFTER refund_amount', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_after_sale' AND COLUMN_NAME='freight_refund_amount')=0,
  'ALTER TABLE dms_shop_after_sale ADD COLUMN freight_refund_amount DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT ''运费退款金额'' AFTER product_refund_amount', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_after_sale' AND COLUMN_NAME='refund_quantity')=0,
  'ALTER TABLE dms_shop_after_sale ADD COLUMN refund_quantity INT NOT NULL DEFAULT 0 COMMENT ''实际退回商品件数'' AFTER freight_refund_amount', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS dms_shop_after_sale_item (
  id BIGINT NOT NULL AUTO_INCREMENT,
  after_sale_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  order_item_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  sku_id BIGINT DEFAULT NULL,
  product_name VARCHAR(256) DEFAULT NULL,
  sku_name VARCHAR(256) DEFAULT NULL,
  refund_quantity INT NOT NULL DEFAULT 0,
  refund_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_after_sale_id (after_sale_id),
  KEY idx_order_item_id (order_item_id),
  KEY idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城售后商品明细表';

UPDATE dms_finance_refund
SET product_refund_amount=refund_amount
WHERE refund_amount>0 AND product_refund_amount=0 AND freight_refund_amount=0;

UPDATE dms_shop_after_sale
SET product_refund_amount=refund_amount
WHERE refund_amount>0 AND product_refund_amount=0 AND freight_refund_amount=0;

INSERT IGNORE INTO dms_finance_risk_rule(rule_code, rule_name, threshold_value, enabled, remark) VALUES
('BONUS_PAYOUT_RATE_MAX', '奖金拨出率预警阈值', 0.35, 1, '运营预警阈值；正式规则理论硬上限为79%（直推65%+董事分红14%）'),
('PROFIT_RATE_MIN', '利润率下限', 0.10, 1, '单笔及汇总利润率低于该值时预警'),
('LOSS_ORDER_COUNT_MAX', '风险订单数上限', 0, 1, '风险订单数大于该值时预警');
