-- 多商户经营闭环：拆分业务控制状态，新增可逐笔复算的商户资金总账与提现状态事件。
SET @schema_name = DATABASE();
SET @controls_existing = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_merchant' AND COLUMN_NAME='account_status');

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_merchant' AND COLUMN_NAME='account_status');
SET @sql = IF(@exists=0,"ALTER TABLE dms_merchant ADD COLUMN account_status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED' AFTER default_settlement_days",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_merchant' AND COLUMN_NAME='business_status');
SET @sql = IF(@exists=0,"ALTER TABLE dms_merchant ADD COLUMN business_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/SUSPENDED/CLOSED' AFTER account_status",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_merchant' AND COLUMN_NAME='fulfillment_status');
SET @sql = IF(@exists=0,"ALTER TABLE dms_merchant ADD COLUMN fulfillment_status VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/PLATFORM_ONLY/DISABLED' AFTER business_status",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_merchant' AND COLUMN_NAME='withdrawal_status');
SET @sql = IF(@exists=0,"ALTER TABLE dms_merchant ADD COLUMN withdrawal_status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/FROZEN' AFTER fulfillment_status",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_merchant' AND COLUMN_NAME='settlement_status');
SET @sql = IF(@exists=0,"ALTER TABLE dms_merchant ADD COLUMN settlement_status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/FROZEN' AFTER withdrawal_status",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_merchant' AND COLUMN_NAME='deposit_status');
SET @sql = IF(@exists=0,"ALTER TABLE dms_merchant ADD COLUMN deposit_status VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/FROZEN' AFTER settlement_status",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_merchant' AND COLUMN_NAME='audit_status');
SET @sql = IF(@exists=0,"ALTER TABLE dms_merchant ADD COLUMN audit_status VARCHAR(16) NOT NULL DEFAULT 'APPROVED' COMMENT 'PENDING/APPROVED/REJECTED' AFTER deposit_status",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_merchant' AND COLUMN_NAME='exit_status');
SET @sql = IF(@exists=0,"ALTER TABLE dms_merchant ADD COLUMN exit_status VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/EXITING/EXITED' AFTER audit_status",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 兼容历史商户：原启用商户继续经营；原停用商户只暂停新成交，仍可登录处理历史订单。
SET @sql = IF(@controls_existing=0,"UPDATE dms_merchant SET business_status=IF(status=1,'ACTIVE','SUSPENDED'), withdrawal_status=IF(status=1,'ENABLED','FROZEN'), settlement_status=IF(status=1,'ENABLED','FROZEN'), fulfillment_status='ENABLED', account_status='ENABLED', audit_status='APPROVED', exit_status='NORMAL'",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS dms_merchant_ledger (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  merchant_name VARCHAR(128) NULL,
  ledger_no VARCHAR(96) NOT NULL,
  biz_type VARCHAR(32) NOT NULL,
  biz_id VARCHAR(96) NOT NULL,
  summary VARCHAR(256) NOT NULL,
  pending_delta DECIMAL(14,2) NOT NULL DEFAULT 0,
  available_delta DECIMAL(14,2) NOT NULL DEFAULT 0,
  frozen_delta DECIMAL(14,2) NOT NULL DEFAULT 0,
  deposit_delta DECIMAL(14,2) NOT NULL DEFAULT 0,
  debt_delta DECIMAL(14,2) NOT NULL DEFAULT 0,
  paid_delta DECIMAL(14,2) NOT NULL DEFAULT 0,
  pending_after DECIMAL(14,2) NOT NULL DEFAULT 0,
  available_after DECIMAL(14,2) NOT NULL DEFAULT 0,
  frozen_after DECIMAL(14,2) NOT NULL DEFAULT 0,
  deposit_after DECIMAL(14,2) NOT NULL DEFAULT 0,
  debt_after DECIMAL(14,2) NOT NULL DEFAULT 0,
  paid_after DECIMAL(14,2) NOT NULL DEFAULT 0,
  operator_id BIGINT NULL,
  operator_name VARCHAR(64) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_merchant_ledger_no (ledger_no),
  KEY idx_merchant_ledger_query (tenant_id,merchant_id,id),
  KEY idx_merchant_ledger_biz (biz_type,biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户资金不可变流水总账';

CREATE TABLE IF NOT EXISTS dms_merchant_withdrawal_event (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  withdrawal_id BIGINT NOT NULL,
  withdrawal_no VARCHAR(64) NOT NULL,
  from_status VARCHAR(24) NULL,
  to_status VARCHAR(24) NOT NULL,
  remark VARCHAR(500) NULL,
  operator_id BIGINT NULL,
  operator_name VARCHAR(64) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_merchant_withdrawal_event (withdrawal_id,id),
  KEY idx_merchant_withdrawal_event_owner (tenant_id,merchant_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户提现状态审计轨迹';
