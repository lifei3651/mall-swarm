-- 提现记录允许仅有商城余额、尚未进入推广体系的会员申请；默认规则仍保持关闭。
SET @schema_name = DATABASE();

SET @withdrawable_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_member_asset_account'
                 AND COLUMN_NAME='withdrawable_balance');
SET @sql = IF(@withdrawable_exists=0,
  'ALTER TABLE dms_member_asset_account ADD COLUMN withdrawable_balance DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT ''可提现余额'' AFTER balance',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 只在首次加列时回填：历史推广会员余额原本均按提现余额使用；普通商城账户历史人工余额不自动纳入。
SET @sql = IF(@withdrawable_exists=0,
  'UPDATE dms_member_asset_account a JOIN dms_agent g ON g.id=a.agent_id SET a.withdrawable_balance=a.balance WHERE g.status=1 AND a.balance>0',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE dms_withdraw_record
    MODIFY COLUMN agent_id BIGINT NULL COMMENT '推广身份ID；普通商城余额持有人提现时为空';

SET @exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_withdraw_record'
                 AND INDEX_NAME='idx_withdraw_user_time');
SET @sql = IF(@exists=0,
  'ALTER TABLE dms_withdraw_record ADD INDEX idx_withdraw_user_time(user_id, create_time)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
