-- 优惠券：先迁移再发布后端，最后发布管理端/H5/小程序。历史订单快照保持 NULL。
-- 默认没有优惠券；只有平台显式确认资金影响并发行后才能领取。
CREATE TABLE IF NOT EXISTS dms_shop_coupon (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 title VARCHAR(60) NOT NULL,
 merchant_id BIGINT NULL,
 merchant_name VARCHAR(200) NOT NULL,
 scope_type VARCHAR(16) NOT NULL,
 product_ids_json TEXT NOT NULL,
 business_types_json VARCHAR(100) NOT NULL,
 amount DECIMAL(12,2) NOT NULL,
 minimum_amount DECIMAL(12,2) NOT NULL,
 merchant_percent INT NOT NULL,
 bonus_basis VARCHAR(8) NOT NULL,
 refund_rule VARCHAR(20) NOT NULL,
 starts_at DATETIME NOT NULL,
 ends_at DATETIME NOT NULL,
 total_count INT NOT NULL,
 per_member_limit INT NOT NULL,
 issued_count INT NOT NULL DEFAULT 0,
 status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
 version INT NOT NULL DEFAULT 0,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_coupon_catalog (tenant_id,status,ends_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS dms_shop_coupon_claim (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 coupon_id BIGINT NOT NULL,
 member_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 request_id VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE',
 order_id BIGINT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_coupon_claim_request (tenant_id,member_id,request_id),
 INDEX idx_coupon_owner (tenant_id,member_id,coupon_id),
 INDEX idx_coupon_order (tenant_id,order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @schema_name=DATABASE();

SET @exists=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_order' AND COLUMN_NAME='coupon_claim_id');
SET @sql=IF(@exists=0,'ALTER TABLE dms_shop_order ADD COLUMN coupon_claim_id BIGINT NULL','SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_order' AND COLUMN_NAME='coupon_title');
SET @sql=IF(@exists=0,'ALTER TABLE dms_shop_order ADD COLUMN coupon_title VARCHAR(60) NULL','SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_order' AND COLUMN_NAME='coupon_refund_rule');
SET @sql=IF(@exists=0,'ALTER TABLE dms_shop_order ADD COLUMN coupon_refund_rule VARCHAR(20) NULL','SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_order_item' AND COLUMN_NAME='coupon_discount_amount');
SET @sql=IF(@exists=0,'ALTER TABLE dms_shop_order_item ADD COLUMN coupon_discount_amount DECIMAL(12,2) NULL','SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_order_item' AND COLUMN_NAME='coupon_merchant_amount');
SET @sql=IF(@exists=0,'ALTER TABLE dms_shop_order_item ADD COLUMN coupon_merchant_amount DECIMAL(12,2) NULL','SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_order_item' AND COLUMN_NAME='coupon_bonus_base_amount');
SET @sql=IF(@exists=0,'ALTER TABLE dms_shop_order_item ADD COLUMN coupon_bonus_base_amount DECIMAL(12,2) NULL','SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_after_sale_item' AND COLUMN_NAME='coupon_bonus_refund_amount');
SET @sql=IF(@exists=0,'ALTER TABLE dms_shop_after_sale_item ADD COLUMN coupon_bonus_refund_amount DECIMAL(12,2) NULL','SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_after_sale_item' AND COLUMN_NAME='coupon_cost_refund_amount');
SET @sql=IF(@exists=0,'ALTER TABLE dms_shop_after_sale_item ADD COLUMN coupon_cost_refund_amount DECIMAL(12,2) NULL','SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
