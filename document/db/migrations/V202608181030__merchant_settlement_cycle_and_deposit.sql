-- 商户结算周期与保证金：历史订单保持0天等待，保证金与提现冻结分账。
SET @schema_name = DATABASE();

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_merchant' AND COLUMN_NAME='default_settlement_days');
SET @sql = IF(@exists=0,"ALTER TABLE dms_merchant ADD COLUMN default_settlement_days INT NOT NULL DEFAULT 0 COMMENT '售后窗口结束后的结算等待天数' AFTER settlement_mode",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_product' AND COLUMN_NAME='settlement_delay_days_override');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_product ADD COLUMN settlement_delay_days_override INT NULL COMMENT '空跟随商户默认，0-365单品覆盖' AFTER cost_amount",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_order_item' AND COLUMN_NAME='settlement_delay_days');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_order_item ADD COLUMN settlement_delay_days INT NOT NULL DEFAULT 0 COMMENT '下单时锁定的商户结算等待天数' AFTER total_cost",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_merchant_settlement' AND COLUMN_NAME='settlement_delay_days');
SET @sql = IF(@exists=0,"ALTER TABLE dms_merchant_settlement ADD COLUMN settlement_delay_days INT NOT NULL DEFAULT 0 COMMENT '下单时锁定的商户结算等待天数' AFTER reversed_amount",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_merchant_settlement' AND COLUMN_NAME='eligible_time');
SET @sql = IF(@exists=0,"ALTER TABLE dms_merchant_settlement ADD COLUMN eligible_time DATETIME NULL COMMENT '确认收货时固化的预计可结算时间' AFTER settlement_delay_days",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_merchant_settlement' AND INDEX_NAME='idx_merchant_settlement_eligible');
SET @sql = IF(@exists=0,"ALTER TABLE dms_merchant_settlement ADD INDEX idx_merchant_settlement_eligible(status,eligible_time,order_id)",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_merchant_account' AND COLUMN_NAME='deposit_frozen_amount');
SET @sql = IF(@exists=0,"ALTER TABLE dms_merchant_account ADD COLUMN deposit_frozen_amount DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '平台冻结保证金' AFTER frozen_amount",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS dms_merchant_deposit_flow (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  merchant_id BIGINT NOT NULL,
  operation_no VARCHAR(64) NOT NULL,
  operation_type VARCHAR(16) NOT NULL COMMENT 'FREEZE/RELEASE',
  amount DECIMAL(14,2) NOT NULL,
  balance_after DECIMAL(14,2) NOT NULL,
  reason VARCHAR(256) NOT NULL,
  operator_id BIGINT NULL,
  operator_name VARCHAR(64) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_merchant_deposit_operation (operation_no),
  KEY idx_merchant_deposit_flow (tenant_id,merchant_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户保证金冻结解冻流水';

-- 已绑定商户的工作台账号增加“仅限本商户货款”的读取与提现申请权限；
-- 服务端仍按 merchant_id 强制隔离，不能访问平台财务或其他商户。
UPDATE dms_admin_user
SET permissions=CONCAT_WS(',', NULLIF(permissions,''),
    IF(FIND_IN_SET('finance:read',COALESCE(permissions,''))=0,'finance:read',NULL),
    IF(FIND_IN_SET('finance:manage',COALESCE(permissions,''))=0,'finance:manage',NULL)),
    update_time=CURRENT_TIMESTAMP
WHERE merchant_id IS NOT NULL;
