-- Add only storage for the optional direct-referral base policy. Existing active
-- customer policies and historical payouts are intentionally not switched/backfilled.
SET @schema_name = DATABASE();
SET @column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_commission_rule_version' AND COLUMN_NAME='direct_referral_config');
SET @migration_sql = IF(@column_exists=0,"ALTER TABLE dms_commission_rule_version ADD COLUMN direct_referral_config TEXT NULL COMMENT '直接推荐规则不可变JSON快照'",'SELECT 1');
PREPARE stmt FROM @migration_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- First payment is tenant-scoped and is never reset by a later refund. In a
-- grouped payment the application treats all children of this order's trade as
-- one first payment. Backfill includes paid orders even if they are now closed.
CREATE TABLE IF NOT EXISTS dms_member_first_payment (
  tenant_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  first_order_id BIGINT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (tenant_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购买者首笔支付标记（退款不清除）';
INSERT IGNORE INTO dms_member_first_payment (tenant_id,user_id,first_order_id)
SELECT paid.tenant_id,paid.user_id,paid.id
FROM dms_shop_order paid
WHERE paid.pay_time IS NOT NULL AND paid.user_id > 0
  AND NOT EXISTS (
    SELECT 1 FROM dms_shop_order earlier
    WHERE earlier.tenant_id=paid.tenant_id AND earlier.user_id=paid.user_id
      AND earlier.pay_time IS NOT NULL
      AND (earlier.pay_time < paid.pay_time OR (earlier.pay_time=paid.pay_time AND earlier.id < paid.id))
  );

-- Preserve attribution of debt offsets when a settled direct-referral order is
-- refunded: offset entries link the original debt; reversal links the offset.
SET @column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_commission_clawback' AND COLUMN_NAME='source_clawback_id');
SET @migration_sql = IF(@column_exists=0,"ALTER TABLE dms_commission_clawback ADD COLUMN source_clawback_id BIGINT NULL COMMENT '原欠债或抵扣记录ID，直接推荐退款追溯使用'",'SELECT 1');
PREPARE stmt FROM @migration_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Buyers without promotion qualification still require an immutable level-0
-- payment anchor. Old snapshots retain their original values and NULL flags.
SET @column_nullable = (SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_order_relation_snapshot' AND COLUMN_NAME='owner_agent_id');
SET @migration_sql = IF(@column_nullable='NO','ALTER TABLE dms_order_relation_snapshot MODIFY COLUMN owner_agent_id BIGINT NULL','SELECT 1');
PREPARE stmt FROM @migration_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @column_nullable = (SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_order_relation_snapshot' AND COLUMN_NAME='target_agent_id');
SET @migration_sql = IF(@column_nullable='NO','ALTER TABLE dms_order_relation_snapshot MODIFY COLUMN target_agent_id BIGINT NULL','SELECT 1');
PREPARE stmt FROM @migration_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_order_relation_snapshot' AND COLUMN_NAME='first_paid_order_eligible');
SET @migration_sql = IF(@column_exists=0,"ALTER TABLE dms_order_relation_snapshot ADD COLUMN first_paid_order_eligible TINYINT NULL COMMENT '支付时是否首次支付订单'",'SELECT 1');
PREPARE stmt FROM @migration_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='dms_order_relation_snapshot' AND COLUMN_NAME='target_promotion_eligible');
SET @migration_sql = IF(@column_exists=0,"ALTER TABLE dms_order_relation_snapshot ADD COLUMN target_promotion_eligible TINYINT NULL COMMENT '支付时目标是否有推广资格'",'SELECT 1');
PREPARE stmt FROM @migration_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
