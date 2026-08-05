-- 余额流水审计字段兼容迁移。
-- 用 information_schema 判断列是否存在，可在已部分执行的环境重复运行，不删除、不改写历史流水。

SET @add_balance_before = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE dms_member_asset_flow ADD COLUMN balance_before decimal(14,2) DEFAULT NULL COMMENT ''变动前余额'' AFTER amount',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'dms_member_asset_flow'
      AND column_name = 'balance_before'
);
PREPARE stmt FROM @add_balance_before;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_operator_id = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE dms_member_asset_flow ADD COLUMN operator_id bigint DEFAULT NULL COMMENT ''执行管理员ID；系统流水为0或空'' AFTER balance_after',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'dms_member_asset_flow'
      AND column_name = 'operator_id'
);
PREPARE stmt FROM @add_operator_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_operator_name = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE dms_member_asset_flow ADD COLUMN operator_name varchar(64) DEFAULT NULL COMMENT ''执行管理员账号；系统流水为system'' AFTER operator_id',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'dms_member_asset_flow'
      AND column_name = 'operator_name'
);
PREPARE stmt FROM @add_operator_name;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
