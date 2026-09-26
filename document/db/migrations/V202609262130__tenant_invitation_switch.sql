-- Existing storefronts retain invitation behavior. TenantServiceImpl starts new storefronts with invitation disabled.
-- A closed invitation module does not erase historical relationships, orders, bonuses or audit records.
SET @schema_name = DATABASE();
SET @invitation_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_tenant' AND COLUMN_NAME='invitation_enabled');
SET @invitation_sql = IF(@invitation_exists=0,"ALTER TABLE dms_tenant ADD COLUMN invitation_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许新增邀请关系'",'SELECT 1');
PREPARE stmt FROM @invitation_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
