-- 为 AES-256-GCM 版本化密文扩充字段容量；应用启动后会分批迁移历史明文。
ALTER TABLE dms_agent
    MODIFY COLUMN id_card VARCHAR(256) DEFAULT NULL COMMENT '身份证号（应用层加密存储）',
    MODIFY COLUMN bank_account VARCHAR(512) DEFAULT NULL COMMENT '银行账号（应用层加密存储）';

ALTER TABLE dms_erp_integration
    MODIFY COLUMN app_secret TEXT DEFAULT NULL COMMENT 'ERP应用密钥（应用层加密存储）',
    MODIFY COLUMN callback_token VARCHAR(1024) DEFAULT NULL COMMENT 'ERP回调令牌（应用层加密存储）';

ALTER TABLE dms_withdraw_record
    MODIFY COLUMN bank_account VARCHAR(512) DEFAULT NULL COMMENT '收款银行账号（应用层加密存储）';

ALTER TABLE dms_merchant
    MODIFY COLUMN bank_account_no VARCHAR(512) DEFAULT NULL COMMENT '商户收款账号（应用层加密存储）';

ALTER TABLE dms_merchant_withdrawal
    MODIFY COLUMN bank_account_no_snapshot VARCHAR(512) DEFAULT NULL COMMENT '提现收款账号快照（应用层加密存储）';
