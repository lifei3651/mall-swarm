-- 商品限购：按会员累计限制购买数量，0 表示不限购。
-- 本脚本只新增字段，不删除或修改商品、订单及会员数据；执行前请按线上发布流程完成数据库备份。

SET @purchase_limit_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'dms_shop_product'
      AND COLUMN_NAME = 'purchase_limit'
);
SET @purchase_limit_sql := IF(
    @purchase_limit_exists = 0,
    'ALTER TABLE `dms_shop_product` ADD COLUMN `purchase_limit` INT NOT NULL DEFAULT 0 AFTER `stock`',
    'SELECT 1'
);
PREPARE purchase_limit_stmt FROM @purchase_limit_sql;
EXECUTE purchase_limit_stmt;
DEALLOCATE PREPARE purchase_limit_stmt;
