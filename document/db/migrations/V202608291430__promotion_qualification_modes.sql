-- 邀请关系与推广资格解耦。
-- 新客户安全默认关闭；只有本次首次增加字段时，才把已有且已启用奖金程序的商城迁移为历史首单模式。

SET @schema_name = DATABASE();
SET @promotion_join_mode_existed = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_tenant' AND COLUMN_NAME='promotion_join_mode'
);

SET @sql = IF(@promotion_join_mode_existed=0,
              "ALTER TABLE dms_tenant ADD COLUMN promotion_join_mode VARCHAR(32) NOT NULL DEFAULT 'DISABLED' COMMENT '推广资格开通方式:DISABLED/AUTO_ON_INVITE/MANUAL_REVIEW/FIRST_PAID_ORDER' AFTER after_sale_window_days",
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 仅首次升级旧库时执行兼容迁移；重复执行绝不覆盖客户后来在后台保存的选择。
SET @sql = IF(@promotion_join_mode_existed=0,
              "UPDATE dms_tenant t SET t.promotion_join_mode = CASE WHEN EXISTS (SELECT 1 FROM dms_commission_rule_version v WHERE v.tenant_id=t.id AND v.status=1 AND v.version_no<>'CUSTOMER_BONUS_DISABLED') THEN 'FIRST_PAID_ORDER' ELSE 'DISABLED' END",
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
