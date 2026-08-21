-- 给佣金追回及人工结算快照补齐客户租户边界。
-- 历史版本只有默认客户数据，因此旧行归入 tenant_id=1；升级后所有读写均由当前会话租户限定。
SET @schema_name = DATABASE();

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_commission_clawback' AND COLUMN_NAME='tenant_id');
SET @sql = IF(@exists=0,"ALTER TABLE dms_commission_clawback ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '商城客户ID' AFTER id",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_commission_clawback' AND INDEX_NAME='idx_clawback_tenant_order');
SET @sql = IF(@exists=0,"ALTER TABLE dms_commission_clawback ADD INDEX idx_clawback_tenant_order(tenant_id,order_id)",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_commission_clawback' AND INDEX_NAME='idx_clawback_tenant_agent');
SET @sql = IF(@exists=0,"ALTER TABLE dms_commission_clawback ADD INDEX idx_clawback_tenant_agent(tenant_id,agent_id)",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_commission_clawback' AND INDEX_NAME='idx_clawback_tenant_record');
SET @sql = IF(@exists=0,"ALTER TABLE dms_commission_clawback ADD INDEX idx_clawback_tenant_record(tenant_id,commission_record_id)",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_commission_settlement_batch' AND COLUMN_NAME='tenant_id');
SET @sql = IF(@exists=0,"ALTER TABLE dms_commission_settlement_batch ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '商城客户ID' AFTER id",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_commission_settlement_batch' AND INDEX_NAME='idx_settlement_batch_tenant_status');
SET @sql = IF(@exists=0,"ALTER TABLE dms_commission_settlement_batch ADD INDEX idx_settlement_batch_tenant_status(tenant_id,status,create_time)",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_commission_settlement_item' AND COLUMN_NAME='tenant_id');
SET @sql = IF(@exists=0,"ALTER TABLE dms_commission_settlement_item ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '商城客户ID' AFTER id",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_commission_settlement_item' AND INDEX_NAME='idx_settlement_item_tenant_batch');
SET @sql = IF(@exists=0,"ALTER TABLE dms_commission_settlement_item ADD INDEX idx_settlement_item_tenant_batch(tenant_id,batch_id,id)",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
