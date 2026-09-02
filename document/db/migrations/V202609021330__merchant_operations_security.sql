-- 商城基座：后台一次性临时凭据、账号默认物流商、商户子账号负责人和结构化商品审核。
SET @schema_name = DATABASE();

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_admin_user' AND COLUMN_NAME='credential_expires_at');
SET @sql = IF(@exists=0,"ALTER TABLE dms_admin_user ADD COLUMN credential_expires_at DATETIME NULL COMMENT '一次性临时凭据到期时间；正式密码为空' AFTER must_change_password",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_admin_user' AND COLUMN_NAME='default_logistics_company');
SET @sql = IF(@exists=0,"ALTER TABLE dms_admin_user ADD COLUMN default_logistics_company VARCHAR(50) NULL COMMENT '当前后台账号导单默认物流公司' AFTER credential_expires_at",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_merchant_product_review' AND COLUMN_NAME='review_checklist_json');
SET @sql = IF(@exists=0,"ALTER TABLE dms_merchant_product_review ADD COLUMN review_checklist_json LONGTEXT NULL COMMENT '结构化审核项目及逐项结果JSON' AFTER review_remark",'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 旧版已经处于“首次登录必须改密”的账号也收口为短时凭据；重复执行不会延长已确定的到期时间。
UPDATE dms_admin_user
SET credential_expires_at=DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 24 HOUR), update_time=CURRENT_TIMESTAMP
WHERE must_change_password=1 AND credential_expires_at IS NULL;

-- 每个既有商户只把最早创建的绑定账号升级为负责人，避免全部历史子账号都获得账号管理能力。
UPDATE dms_admin_user u
JOIN (
  SELECT merchant_id, MIN(id) AS owner_id
  FROM dms_admin_user
  WHERE merchant_id IS NOT NULL
  GROUP BY merchant_id
) owner ON owner.owner_id=u.id
SET u.role_code='MERCHANT_OWNER',
    u.permissions=CASE
      WHEN FIND_IN_SET('merchant:staff-manage', REPLACE(COALESCE(u.permissions,''), ' ', '')) > 0 THEN u.permissions
      WHEN COALESCE(TRIM(u.permissions),'')='' THEN 'admin:read,merchant:staff-manage'
      ELSE CONCAT(u.permissions, ',merchant:staff-manage')
    END,
    u.update_time=CURRENT_TIMESTAMP
WHERE u.merchant_id IS NOT NULL;
