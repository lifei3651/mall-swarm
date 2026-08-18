-- 多商户退出、提现异常状态与资金对账闭环。
SET @schema_name = DATABASE();

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_merchant_withdrawal' AND COLUMN_NAME='resume_status');
SET @sql = IF(@exists=0,"ALTER TABLE dms_merchant_withdrawal ADD COLUMN resume_status VARCHAR(24) NULL COMMENT '风控冻结前状态' AFTER status",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 每个商户、业务类型、业务主键只能形成一笔账，数据库层阻止重复入账。
SET @exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_merchant_ledger' AND INDEX_NAME='uk_merchant_ledger_biz');
SET @sql = IF(@exists=0,"ALTER TABLE dms_merchant_ledger ADD UNIQUE INDEX uk_merchant_ledger_biz(tenant_id,merchant_id,biz_type,biz_id)",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 迁移前已有账户补一期初流水；已有任何流水的账户不倒序插入，避免破坏现有账本顺序。
INSERT INTO dms_merchant_ledger(
  tenant_id,merchant_id,merchant_name,ledger_no,biz_type,biz_id,summary,
  pending_delta,available_delta,frozen_delta,deposit_delta,debt_delta,paid_delta,
  pending_after,available_after,frozen_after,deposit_after,debt_after,paid_after
)
SELECT a.tenant_id,a.merchant_id,m.merchant_name,
       CONCAT('MLOPEN',a.tenant_id,'-',a.merchant_id),'OPENING_BALANCE',CAST(a.merchant_id AS CHAR),'商户资金账本期初余额',
       a.pending_amount,a.available_amount,a.frozen_amount,a.deposit_frozen_amount,a.debt_amount,a.total_paid_amount,
       a.pending_amount,a.available_amount,a.frozen_amount,a.deposit_frozen_amount,a.debt_amount,a.total_paid_amount
FROM dms_merchant_account a
INNER JOIN dms_merchant m ON m.id=a.merchant_id
WHERE NOT EXISTS (SELECT 1 FROM dms_merchant_ledger l WHERE l.tenant_id=a.tenant_id AND l.merchant_id=a.merchant_id);
