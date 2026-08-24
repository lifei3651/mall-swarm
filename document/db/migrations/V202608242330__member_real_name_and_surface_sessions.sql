-- 1.0.84 会员实名认证与多端资金权限边界。
-- 姓名、身份证号由应用层 AES-GCM 加密；同一身份证可认证多个账号，因此严禁增加身份证唯一索引。

SET @schema_name = DATABASE();
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_member_session' AND COLUMN_NAME='surface');
SET @sql = IF(@exists=0,
              "ALTER TABLE dms_shop_member_session ADD COLUMN surface VARCHAR(16) NOT NULL DEFAULT 'legacy' AFTER token",
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS dms_member_real_name (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    member_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    real_name VARCHAR(512) NOT NULL COMMENT 'AES-GCM密文；禁止明文日志和导出',
    id_card VARCHAR(512) NOT NULL COMMENT 'AES-GCM密文；禁止建立唯一索引',
    provider VARCHAR(32) NOT NULL,
    provider_request_id VARCHAR(128) NULL,
    consent_version VARCHAR(64) NOT NULL,
    consent_time DATETIME NOT NULL,
    verified_time DATETIME NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_real_name_account (tenant_id,member_id),
    KEY idx_member_real_name_user (tenant_id,user_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员账号实名认证；一证允许多账号，每账号只绑定一份';

CREATE TABLE IF NOT EXISTS dms_member_real_name_attempt (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    member_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    provider VARCHAR(32) NOT NULL,
    result_code VARCHAR(32) NOT NULL,
    matched TINYINT NOT NULL DEFAULT 0,
    provider_request_id VARCHAR(128) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_real_name_attempt_member (tenant_id,member_id,create_time),
    KEY idx_real_name_attempt_risk (tenant_id,matched,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实名认证核验审计；不保存姓名和身份证号';
