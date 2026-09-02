-- 安全修复：后台一次性凭据原子消费，以及会员奖金官方渠道打款证据闭环。
SET @schema_name = DATABASE();

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_admin_user'
                 AND COLUMN_NAME='credential_consumed_at');
SET @sql = IF(@exists=0,
  "ALTER TABLE dms_admin_user ADD COLUMN credential_consumed_at DATETIME NULL COMMENT '一次性临时凭据首次成功登录消费时间' AFTER credential_expires_at",
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS dms_withdrawal_payout (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
  withdraw_id BIGINT NOT NULL COMMENT '提现记录ID',
  withdraw_no VARCHAR(64) NOT NULL COMMENT '提现单号',
  attempt_no INT NOT NULL DEFAULT 1 COMMENT '仅在官方明确失败后递增',
  request_no VARCHAR(64) NOT NULL COMMENT '发送给官方渠道的幂等业务单号',
  channel VARCHAR(16) NOT NULL COMMENT 'WECHAT/ALIPAY',
  state VARCHAR(32) NOT NULL COMMENT 'PROCESSING/WAIT_USER_CONFIRM/SUCCESS/FAILED/UNKNOWN',
  provider_status VARCHAR(32) DEFAULT NULL COMMENT '渠道原始状态码',
  provider_order_no VARCHAR(128) DEFAULT NULL COMMENT '渠道打款单号',
  amount DECIMAL(10,2) NOT NULL COMMENT '渠道请求金额快照',
  recipient_hash CHAR(64) DEFAULT NULL COMMENT '渠道及收款身份不可逆摘要',
  response_digest CHAR(64) DEFAULT NULL COMMENT '核验响应关键字段摘要',
  failure_code VARCHAR(64) DEFAULT NULL COMMENT '安全清洗后的失败码',
  confirmation_package VARCHAR(2048) DEFAULT NULL COMMENT '微信用户确认包（应用层加密）',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_withdrawal_payout_withdraw_id (withdraw_id),
  UNIQUE KEY uk_withdrawal_payout_request_no (request_no),
  UNIQUE KEY uk_withdrawal_payout_provider_order_no (provider_order_no),
  KEY idx_withdrawal_payout_state (state, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员奖金提现官方渠道打款证据';
