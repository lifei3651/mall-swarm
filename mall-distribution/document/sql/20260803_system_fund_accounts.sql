-- 系统资金账户与普通商城会员分离。
-- 此迁移只补充结构；生产测试数据清理及账户重建由
-- 20260803_clean_test_business_data.sql 在完整备份后执行。

SET @schema_name := DATABASE();
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @schema_name
       AND TABLE_NAME = 'dms_shop_member'
       AND COLUMN_NAME = 'system_account') = 0,
    'ALTER TABLE dms_shop_member ADD COLUMN system_account TINYINT NOT NULL DEFAULT 0 COMMENT ''系统内部资金账户：0-否 1-是'' AFTER status',
    'SELECT 1'
);
PREPARE migration_statement FROM @sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

-- 已创建的内部资金账户补齐稳定账号名。它们只供资金归集服务定位，
-- 不参与客户会员列表、登录、邀请关系或会员数量统计。
UPDATE dms_shop_member
SET username = 'SYSTEM_REMAINDER'
WHERE system_account = 1
  AND user_id = -900000000000000001;

UPDATE dms_shop_member
SET username = 'SYSTEM_PRODUCT_COST'
WHERE system_account = 1
  AND user_id = -900000000000000005;
