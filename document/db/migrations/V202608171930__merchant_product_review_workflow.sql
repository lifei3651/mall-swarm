-- 商户商品工作台与审核：商户账号只能维护自己的商品；审核通过后商品自动上架。
SET @schema_name = DATABASE();

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_admin_user' AND COLUMN_NAME='merchant_id');
SET @sql = IF(@exists=0,"ALTER TABLE dms_admin_user ADD COLUMN merchant_id BIGINT NULL COMMENT '绑定商户后为受限商户工作台账号' AFTER permissions",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_product' AND COLUMN_NAME='merchant_review_status');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_product ADD COLUMN merchant_review_status VARCHAR(16) NULL COMMENT 'DRAFT/PENDING/APPROVED/REJECTED' AFTER status",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_product' AND COLUMN_NAME='merchant_review_version');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_product ADD COLUMN merchant_review_version INT NOT NULL DEFAULT 0 AFTER merchant_review_status",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_product' AND COLUMN_NAME='merchant_review_remark');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_product ADD COLUMN merchant_review_remark VARCHAR(500) NULL AFTER merchant_review_version",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_product' AND COLUMN_NAME='merchant_review_submitted_at');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_product ADD COLUMN merchant_review_submitted_at DATETIME NULL AFTER merchant_review_remark",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_product' AND COLUMN_NAME='merchant_reviewed_at');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_product ADD COLUMN merchant_reviewed_at DATETIME NULL AFTER merchant_review_submitted_at",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_product' AND COLUMN_NAME='merchant_reviewer_id');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_product ADD COLUMN merchant_reviewer_id BIGINT NULL AFTER merchant_reviewed_at",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_shop_product' AND COLUMN_NAME='merchant_reviewer_name');
SET @sql = IF(@exists=0,"ALTER TABLE dms_shop_product ADD COLUMN merchant_reviewer_name VARCHAR(64) NULL AFTER merchant_reviewer_id",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS dms_merchant_product_review (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  merchant_id BIGINT NOT NULL,
  merchant_name VARCHAR(128) NOT NULL,
  product_id BIGINT NOT NULL,
  review_version INT NOT NULL,
  review_type VARCHAR(20) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  product_no VARCHAR(64) NULL,
  product_name VARCHAR(60) NOT NULL,
  sale_price DECIMAL(12,2) NOT NULL,
  settlement_price DECIMAL(12,2) NOT NULL,
  sku_count INT NOT NULL DEFAULT 0,
  product_snapshot LONGTEXT NOT NULL,
  submitter_id BIGINT NULL,
  submitter_name VARCHAR(64) NULL,
  submitted_at DATETIME NOT NULL,
  reviewer_id BIGINT NULL,
  reviewer_name VARCHAR(64) NULL,
  review_remark VARCHAR(500) NULL,
  reviewed_at DATETIME NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_merchant_product_review_version (product_id, review_version),
  KEY idx_merchant_product_review_queue (tenant_id, status, submitted_at, id),
  KEY idx_merchant_product_review_merchant (merchant_id, product_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户商品上架与价格变更审核快照';

-- 已存在的商户商品保持当前可售状态：上架商品视为历史已审核，下架商品进入待编辑草稿。
UPDATE dms_shop_product
SET merchant_review_status=CASE WHEN status=1 THEN 'APPROVED' ELSE 'DRAFT' END,
    merchant_review_version=0
WHERE merchant_id IS NOT NULL AND merchant_review_status IS NULL;
