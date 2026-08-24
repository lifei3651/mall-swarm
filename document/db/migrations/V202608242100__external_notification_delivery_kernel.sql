-- 1.0.83 外部通知发送内核。
-- 只保存任务、脱敏结果、授权摘要和费用数据；不保存手机号、地址、银行卡、验证码或供应商密钥。
-- 所有外部渠道仍由应用启动配置二次门禁，直接修改数据库开关不能触发供应商调用。

SET @schema_name = DATABASE();

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_message_delivery_task' AND COLUMN_NAME='event_type');
SET @sql = IF(@exists=0, "ALTER TABLE dms_message_delivery_task ADD COLUMN event_type VARCHAR(64) NOT NULL DEFAULT 'UNKNOWN' AFTER message_id", 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_message_delivery_task' AND COLUMN_NAME='idempotency_key');
SET @sql = IF(@exists=0, "ALTER TABLE dms_message_delivery_task ADD COLUMN idempotency_key VARCHAR(190) NULL AFTER channel", 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_message_delivery_task' AND COLUMN_NAME='attempt_count');
SET @sql = IF(@exists=0, "ALTER TABLE dms_message_delivery_task ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 AFTER retry_count", 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_message_delivery_task' AND COLUMN_NAME='max_attempts');
SET @sql = IF(@exists=0, "ALTER TABLE dms_message_delivery_task ADD COLUMN max_attempts INT NOT NULL DEFAULT 5 AFTER attempt_count", 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_message_delivery_task' AND COLUMN_NAME='provider_code');
SET @sql = IF(@exists=0, "ALTER TABLE dms_message_delivery_task ADD COLUMN provider_code VARCHAR(32) NULL AFTER estimated_cost", 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_message_delivery_task' AND COLUMN_NAME='actual_cost');
SET @sql = IF(@exists=0, "ALTER TABLE dms_message_delivery_task ADD COLUMN actual_cost DECIMAL(12,4) NOT NULL DEFAULT 0 AFTER estimated_cost", 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_message_delivery_task' AND COLUMN_NAME='lease_owner');
SET @sql = IF(@exists=0, "ALTER TABLE dms_message_delivery_task ADD COLUMN lease_owner VARCHAR(96) NULL AFTER error_message", 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_message_delivery_task' AND COLUMN_NAME='lease_until');
SET @sql = IF(@exists=0, "ALTER TABLE dms_message_delivery_task ADD COLUMN lease_until DATETIME NULL AFTER lease_owner", 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_message_delivery_task' AND COLUMN_NAME='expires_at');
SET @sql = IF(@exists=0, "ALTER TABLE dms_message_delivery_task ADD COLUMN expires_at DATETIME NULL AFTER next_retry_time", 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_message_delivery_task' AND COLUMN_NAME='accepted_time');
SET @sql = IF(@exists=0, "ALTER TABLE dms_message_delivery_task ADD COLUMN accepted_time DATETIME NULL AFTER sent_time", 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_message_delivery_task' AND COLUMN_NAME='delivered_time');
SET @sql = IF(@exists=0, "ALTER TABLE dms_message_delivery_task ADD COLUMN delivered_time DATETIME NULL AFTER accepted_time", 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE dms_message_delivery_task task
JOIN dms_member_message message ON message.id=task.message_id AND message.tenant_id=task.tenant_id
SET task.event_type=message.event_type
WHERE task.event_type='UNKNOWN';
UPDATE dms_message_delivery_task
SET idempotency_key=CONCAT(tenant_id, ':', message_id, ':', channel)
WHERE idempotency_key IS NULL OR idempotency_key='';

SET @exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_message_delivery_task' AND INDEX_NAME='uk_message_delivery_idempotency');
SET @sql = IF(@exists=0, 'CREATE UNIQUE INDEX uk_message_delivery_idempotency ON dms_message_delivery_task (tenant_id,idempotency_key)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_message_delivery_task' AND INDEX_NAME='idx_message_delivery_due');
SET @sql = IF(@exists=0, 'CREATE INDEX idx_message_delivery_due ON dms_message_delivery_task (status,next_retry_time,lease_until,id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS dms_message_delivery_attempt (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    attempt_no INT NOT NULL,
    idempotency_key VARCHAR(190) NOT NULL,
    state VARCHAR(24) NOT NULL,
    provider_code VARCHAR(32) NOT NULL,
    provider_message_id VARCHAR(128) NULL,
    query_count INT NOT NULL DEFAULT 0,
    estimated_cost DECIMAL(12,4) NOT NULL DEFAULT 0,
    actual_cost DECIMAL(12,4) NOT NULL DEFAULT 0,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(255) NULL,
    submitted_time DATETIME NULL,
    resolved_time DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_message_attempt_no (tenant_id,task_id,attempt_no),
    UNIQUE KEY uk_message_attempt_idempotency (tenant_id,idempotency_key),
    KEY idx_message_attempt_task (tenant_id,task_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部通知逐次尝试；只保存脱敏结果';

CREATE TABLE IF NOT EXISTS dms_message_cost_budget (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    scope_type VARCHAR(16) NOT NULL COMMENT 'TENANT EVENT CHANNEL',
    scope_key VARCHAR(64) NOT NULL,
    daily_limit DECIMAL(12,4) NOT NULL DEFAULT 0,
    monthly_limit DECIMAL(12,4) NOT NULL DEFAULT 0,
    currency CHAR(3) NOT NULL DEFAULT 'CNY',
    enabled TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_message_budget_scope (tenant_id,scope_type,scope_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知费用三层硬上限；未配置或为零时禁止外发';

CREATE TABLE IF NOT EXISTS dms_message_recipient_authorization (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    channel VARCHAR(24) NOT NULL,
    endpoint_hash CHAR(64) NOT NULL COMMENT '合格终端或手机号的不可逆摘要',
    authorized TINYINT NOT NULL DEFAULT 0,
    authorized_time DATETIME NULL,
    expires_at DATETIME NULL,
    revoked_time DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_message_recipient_channel (tenant_id,member_id,channel),
    KEY idx_message_recipient_active (tenant_id,channel,authorized,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部通知用户授权与合格终端摘要；不保存原始终端令牌';

CREATE TABLE IF NOT EXISTS dms_message_delivery_receipt (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    channel VARCHAR(24) NOT NULL,
    provider_code VARCHAR(32) NOT NULL,
    receipt_id VARCHAR(128) NOT NULL,
    task_id BIGINT NULL,
    payload_digest CHAR(64) NOT NULL,
    signature_valid TINYINT NOT NULL,
    receipt_status VARCHAR(24) NULL,
    error_code VARCHAR(64) NULL,
    received_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_message_receipt_provider (tenant_id,channel,provider_code,receipt_id),
    KEY idx_message_receipt_task (tenant_id,task_id,received_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='验签后的通知回执摘要；不保存原始报文';

INSERT INTO dms_message_cost_budget (tenant_id,scope_type,scope_key,daily_limit,monthly_limit,currency,enabled)
SELECT 1,'TENANT','*',0,0,'CNY',0
WHERE NOT EXISTS (SELECT 1 FROM dms_message_cost_budget WHERE tenant_id=1 AND scope_type='TENANT' AND scope_key='*');
INSERT INTO dms_message_cost_budget (tenant_id,scope_type,scope_key,daily_limit,monthly_limit,currency,enabled)
SELECT 1,'EVENT',template.event_type,0,0,'CNY',0 FROM dms_message_template template
WHERE template.tenant_id=1 AND NOT EXISTS (
    SELECT 1 FROM dms_message_cost_budget budget WHERE budget.tenant_id=1 AND budget.scope_type='EVENT' AND budget.scope_key=template.event_type
);
INSERT INTO dms_message_cost_budget (tenant_id,scope_type,scope_key,daily_limit,monthly_limit,currency,enabled)
SELECT 1,'CHANNEL',seed.channel,0,0,'CNY',0 FROM (
    SELECT 'SMS' channel UNION ALL SELECT 'APP_PUSH' UNION ALL SELECT 'MINI_PROGRAM'
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM dms_message_cost_budget budget WHERE budget.tenant_id=1 AND budget.scope_type='CHANNEL' AND budget.scope_key=seed.channel
);
